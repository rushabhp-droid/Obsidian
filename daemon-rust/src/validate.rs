//! Every value from the server is parsed here before it can reach iptables or
//! /etc/hosts. Anything that isn't exactly one IP address or one strict,
//! lowercase domain is rejected.

use std::net::IpAddr;

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
pub enum Target {
    Ip(IpAddr),
    Domain(String),
}

pub fn parse(indicator_type: &str, value: &str) -> Result<Target, String> {
    match indicator_type {
        "IP" => value
            .parse::<IpAddr>()
            .map(Target::Ip)
            .map_err(|_| format!("not a single IP address: {value:?}")),
        "DOMAIN" if is_strict_domain(value) => Ok(Target::Domain(value.to_string())),
        "DOMAIN" => Err(format!("not a strict domain: {value:?}")),
        other => Err(format!("indicator type {other:?} is not enforced by the daemon")),
    }
}

/// RFC 1123 hostname, lowercase, at least two labels, alphabetic TLD.
fn is_strict_domain(value: &str) -> bool {
    if value.is_empty() || value.len() > 253 {
        return false;
    }
    let labels: Vec<&str> = value.split('.').collect();
    if labels.len() < 2 {
        return false;
    }
    let label_ok = |label: &str| {
        (1..=63).contains(&label.len())
            && !label.starts_with('-')
            && !label.ends_with('-')
            && label.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
    };
    let tld = labels[labels.len() - 1];
    labels.iter().all(|label| label_ok(label))
        && tld.len() >= 2
        && tld.bytes().all(|b| b.is_ascii_lowercase())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_ips_and_strict_domains() {
        assert_eq!(parse("IP", "203.0.113.45"), Ok(Target::Ip("203.0.113.45".parse().unwrap())));
        assert_eq!(parse("IP", "2001:db8::1"), Ok(Target::Ip("2001:db8::1".parse().unwrap())));
        assert_eq!(
            parse("DOMAIN", "testsafebrowsing.appspot.com"),
            Ok(Target::Domain("testsafebrowsing.appspot.com".into()))
        );
        assert!(parse("DOMAIN", "login-verify.example").is_ok());
    }

    #[test]
    fn rejects_anything_that_could_inject_into_a_command_or_hosts_line() {
        for bad in [
            "evil.example\n0.0.0.0 bank.example",
            "evil.example # OBSIDIAN-MANAGED",
            "evil.example bank.example",
            "evil.example;rm -rf /",
            "$(reboot).example",
            "-j.example",
            "localhost",
            "Evil.Example",
            "203.0.113.45",
            "*.evil.example",
            "evil..example",
            "",
        ] {
            assert!(parse("DOMAIN", bad).is_err(), "accepted domain {bad:?}");
        }
        for bad in ["203.0.113.45/24", "203.0.113.45:80", "fe80::1%eth0", " 203.0.113.45", "-j ACCEPT", "1.2.3"] {
            assert!(parse("IP", bad).is_err(), "accepted ip {bad:?}");
        }
    }

    #[test]
    fn other_indicator_types_are_not_enforced() {
        assert!(parse("URL", "http://evil.example/").is_err());
        assert!(parse("EMAIL", "a@evil.example").is_err());
    }
}
