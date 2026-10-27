//! iptables / ip6tables, only inside the dedicated OBSIDIAN chain. Binaries are
//! run with an argument list (Command::new(..).args(..)), never through a shell.

use std::net::IpAddr;
use std::process::{Command, Stdio};

pub const CHAIN: &str = "OBSIDIAN";

/// Runs one program with arguments; returns whether it exited successfully.
pub trait Runner {
    fn run(&mut self, program: &str, args: &[&str]) -> bool;
}

pub struct SystemRunner;

impl Runner for SystemRunner {
    fn run(&mut self, program: &str, args: &[&str]) -> bool {
        Command::new(program)
            .args(args)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map(|status| status.success())
            .unwrap_or(false)
    }
}

/// Prints the commands instead of running them; existence checks report "absent".
pub struct DryRunner;

impl Runner for DryRunner {
    fn run(&mut self, program: &str, args: &[&str]) -> bool {
        if args.first() == Some(&"-C") {
            return false;
        }
        eprintln!("[dry-run] {program} {}", args.join(" "));
        true
    }
}

fn binary(ip: &IpAddr) -> &'static str {
    if ip.is_ipv4() { "iptables" } else { "ip6tables" }
}

/// Creates the chain and the INPUT/OUTPUT jumps if they don't exist yet.
pub fn ensure_chain(runner: &mut dyn Runner) {
    for bin in ["iptables", "ip6tables"] {
        runner.run(bin, &["-N", CHAIN]);
        for parent in ["INPUT", "OUTPUT"] {
            if !runner.run(bin, &["-C", parent, "-j", CHAIN]) {
                runner.run(bin, &["-I", parent, "-j", CHAIN]);
            }
        }
    }
}

/// Drops traffic from and to the address (incoming and outgoing).
pub fn block(runner: &mut dyn Runner, ip: &IpAddr) {
    let address = ip.to_string();
    for flag in ["-s", "-d"] {
        let rule = [CHAIN, flag, address.as_str(), "-j", "DROP"];
        if !runner.run(binary(ip), &[&["-C"][..], &rule].concat()) {
            runner.run(binary(ip), &[&["-A"][..], &rule].concat());
        }
    }
}

pub fn unblock(runner: &mut dyn Runner, ip: &IpAddr) {
    let address = ip.to_string();
    for flag in ["-s", "-d"] {
        runner.run(binary(ip), &["-D", CHAIN, flag, address.as_str(), "-j", "DROP"]);
    }
}

/// Cleanup is just flushing the chain.
pub fn flush(runner: &mut dyn Runner) {
    for bin in ["iptables", "ip6tables"] {
        runner.run(bin, &["-F", CHAIN]);
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;

    /// Records every command; `-C` checks report "absent".
    #[derive(Default)]
    pub struct Recorder {
        pub commands: Vec<String>,
    }

    impl Runner for Recorder {
        fn run(&mut self, program: &str, args: &[&str]) -> bool {
            self.commands.push(format!("{program} {}", args.join(" ")));
            args.first() != Some(&"-C")
        }
    }

    #[test]
    fn blocking_an_ipv4_drops_both_directions_in_the_obsidian_chain() {
        let mut recorder = Recorder::default();
        block(&mut recorder, &"203.0.113.45".parse().unwrap());
        assert_eq!(
            recorder.commands,
            [
                "iptables -C OBSIDIAN -s 203.0.113.45 -j DROP",
                "iptables -A OBSIDIAN -s 203.0.113.45 -j DROP",
                "iptables -C OBSIDIAN -d 203.0.113.45 -j DROP",
                "iptables -A OBSIDIAN -d 203.0.113.45 -j DROP",
            ]
        );
    }

    #[test]
    fn ipv6_uses_ip6tables_and_unblock_deletes() {
        let mut recorder = Recorder::default();
        unblock(&mut recorder, &"2001:db8::1".parse().unwrap());
        assert_eq!(
            recorder.commands,
            ["ip6tables -D OBSIDIAN -s 2001:db8::1 -j DROP", "ip6tables -D OBSIDIAN -d 2001:db8::1 -j DROP"]
        );
    }

    #[test]
    fn chain_is_jumped_to_from_input_and_output() {
        let mut recorder = Recorder::default();
        ensure_chain(&mut recorder);
        assert!(recorder.commands.contains(&"iptables -N OBSIDIAN".to_string()));
        assert!(recorder.commands.contains(&"iptables -I INPUT -j OBSIDIAN".to_string()));
        assert!(recorder.commands.contains(&"iptables -I OUTPUT -j OBSIDIAN".to_string()));
        assert!(recorder.commands.contains(&"ip6tables -I OUTPUT -j OBSIDIAN".to_string()));
    }
}
