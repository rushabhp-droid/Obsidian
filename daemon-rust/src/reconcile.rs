//! Applies the server's rule set. SYNC flushes everything the daemon manages
//! and applies exactly the full set, so changes missed while offline are not lost.

use crate::firewall::{self, Runner};
use crate::protocol::RuleEntry;
use crate::sinkhole::Sinkhole;
use crate::validate::{self, Target};
use std::collections::BTreeSet;
use std::net::IpAddr;

pub struct Enforcer {
    runner: Box<dyn Runner + Send>,
    sinkhole: Sinkhole,
    ips: BTreeSet<IpAddr>,
    domains: BTreeSet<String>,
}

impl Enforcer {
    pub fn new(runner: Box<dyn Runner + Send>, sinkhole: Sinkhole) -> Self {
        Enforcer { runner, sinkhole, ips: BTreeSet::new(), domains: BTreeSet::new() }
    }

    pub fn prepare(&mut self) {
        firewall::ensure_chain(self.runner.as_mut());
    }

    /// Replaces everything this daemon enforces with `rules`. Returns (applied, rejected).
    pub fn sync(&mut self, rules: &[RuleEntry]) -> (usize, usize) {
        let mut ips = BTreeSet::new();
        let mut domains = BTreeSet::new();
        let mut rejected = 0;
        for rule in rules {
            match validate::parse(&rule.indicator_type, &rule.value) {
                Ok(Target::Ip(ip)) => {
                    ips.insert(ip);
                }
                Ok(Target::Domain(domain)) => {
                    domains.insert(domain);
                }
                Err(reason) => {
                    rejected += 1;
                    eprintln!("obsidian: skipped rule {}: {reason}", rule.rule_id);
                }
            }
        }
        firewall::flush(self.runner.as_mut());
        for ip in &ips {
            firewall::block(self.runner.as_mut(), ip);
        }
        self.ips = ips;
        self.domains = domains;
        self.write_hosts();
        (self.ips.len() + self.domains.len(), rejected)
    }

    pub fn block(&mut self, rule: &RuleEntry) -> Result<(), String> {
        match validate::parse(&rule.indicator_type, &rule.value)? {
            Target::Ip(ip) => {
                firewall::block(self.runner.as_mut(), &ip);
                self.ips.insert(ip);
            }
            Target::Domain(domain) => {
                if self.domains.insert(domain) {
                    self.write_hosts();
                }
            }
        }
        Ok(())
    }

    pub fn bypass(&mut self, rule: &RuleEntry) -> Result<(), String> {
        match validate::parse(&rule.indicator_type, &rule.value)? {
            Target::Ip(ip) => {
                firewall::unblock(self.runner.as_mut(), &ip);
                self.ips.remove(&ip);
            }
            Target::Domain(domain) => {
                if self.domains.remove(&domain) {
                    self.write_hosts();
                }
            }
        }
        Ok(())
    }

    /// On exit: flush the chain and remove every managed hosts line.
    pub fn cleanup(&mut self) {
        firewall::flush(self.runner.as_mut());
        self.ips.clear();
        self.domains.clear();
        self.write_hosts();
    }

    pub fn blocked(&self) -> (usize, usize) {
        (self.ips.len(), self.domains.len())
    }

    fn write_hosts(&mut self) {
        if let Err(e) = self.sinkhole.write(&self.domains) {
            eprintln!("obsidian: writing {} failed: {e}", self.sinkhole.path().display());
        }
        // systemd-resolved caches answers; other resolvers just make this a no-op.
        self.runner.run("resolvectl", &["flush-caches"]);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::sync::{Arc, Mutex};

    type Calls = Vec<(String, Vec<String>)>;

    #[derive(Clone, Default)]
    struct Shared(Arc<Mutex<Calls>>);

    impl Runner for Shared {
        fn run(&mut self, program: &str, args: &[&str]) -> bool {
            self.0.lock().unwrap().push((program.to_string(), args.iter().map(|a| a.to_string()).collect()));
            args.first() != Some(&"-C")
        }
    }

    fn rule(kind: &str, value: &str) -> RuleEntry {
        RuleEntry { rule_id: format!("rule-{value}"), indicator_type: kind.into(), value: value.into() }
    }

    fn enforcer() -> (Enforcer, Shared, tempfile::TempDir) {
        let dir = tempfile::tempdir().unwrap();
        let hosts = dir.path().join("hosts");
        fs::write(&hosts, "127.0.0.1 localhost\n").unwrap();
        let runner = Shared::default();
        (Enforcer::new(Box::new(runner.clone()), Sinkhole::new(hosts)), runner, dir)
    }

    fn hosts(dir: &tempfile::TempDir) -> String {
        fs::read_to_string(dir.path().join("hosts")).unwrap()
    }

    #[test]
    fn sync_flushes_then_applies_exactly_the_valid_rules() {
        let (mut enforcer, runner, dir) = enforcer();
        enforcer.block(&rule("DOMAIN", "stale.example")).unwrap();

        let (applied, rejected) = enforcer.sync(&[
            rule("DOMAIN", "testsafebrowsing.appspot.com"),
            rule("IP", "203.0.113.45"),
            rule("DOMAIN", "evil.example\n0.0.0.0 bank.example"),
            rule("IP", "203.0.113.45 -j ACCEPT"),
        ]);

        assert_eq!((applied, rejected), (2, 2));
        assert_eq!(hosts(&dir), "127.0.0.1 localhost\n0.0.0.0 testsafebrowsing.appspot.com # OBSIDIAN-MANAGED\n");
        let commands = runner.0.lock().unwrap();
        let flush = commands.iter().position(|(p, a)| p == "iptables" && a == &["-F", "OBSIDIAN"]).unwrap();
        let add = commands.iter().position(|(_, a)| a.first().map(String::as_str) == Some("-A")).unwrap();
        assert!(flush < add, "flush must come before re-applying");
        assert!(commands.iter().all(|(_, args)| !args.iter().any(|a| a.contains("bank.example") || a.contains("ACCEPT"))));
    }

    #[test]
    fn bypass_lifts_and_block_reapplies() {
        let (mut enforcer, runner, dir) = enforcer();
        enforcer.sync(&[rule("DOMAIN", "evil.example"), rule("IP", "198.51.100.7")]);

        enforcer.bypass(&rule("DOMAIN", "evil.example")).unwrap();
        enforcer.bypass(&rule("IP", "198.51.100.7")).unwrap();
        assert_eq!(hosts(&dir), "127.0.0.1 localhost\n");
        assert!(runner.0.lock().unwrap().iter().any(|(p, a)| p == "iptables" && a[0] == "-D" && a[3] == "198.51.100.7"));

        enforcer.block(&rule("DOMAIN", "evil.example")).unwrap();
        assert!(hosts(&dir).contains("0.0.0.0 evil.example # OBSIDIAN-MANAGED"));
    }

    #[test]
    fn invalid_live_rules_are_refused_without_touching_the_system() {
        let (mut enforcer, runner, _dir) = enforcer();
        assert!(enforcer.block(&rule("DOMAIN", "evil.example; reboot")).is_err());
        assert!(enforcer.block(&rule("URL", "http://evil.example/")).is_err());
        assert!(runner.0.lock().unwrap().is_empty());
    }

    #[test]
    fn cleanup_flushes_the_chain_and_removes_managed_lines() {
        let (mut enforcer, runner, dir) = enforcer();
        enforcer.sync(&[rule("DOMAIN", "evil.example"), rule("IP", "198.51.100.7")]);

        enforcer.cleanup();

        assert_eq!(hosts(&dir), "127.0.0.1 localhost\n");
        assert_eq!(enforcer.blocked(), (0, 0));
        let commands = runner.0.lock().unwrap();
        assert!(commands.iter().rev().any(|(p, a)| p == "iptables" && a == &["-F", "OBSIDIAN"]));
    }

    #[test]
    fn only_the_three_expected_binaries_are_ever_run() {
        let (mut enforcer, runner, _dir) = enforcer();
        enforcer.prepare();
        enforcer.sync(&[rule("DOMAIN", "evil.example"), rule("IP", "2001:db8::1")]);
        enforcer.cleanup();
        assert!(runner.0.lock().unwrap().iter().all(|(p, _)| ["iptables", "ip6tables", "resolvectl"].contains(&p.as_str())));
    }
}
