//! Command line and the bearer token file.

use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::PathBuf;

pub const USAGE: &str = "usage: daemon-rust [--server ws(s)://host/ws/firewall] [--token-file PATH] [--hosts-file PATH] [--dry-run]

  --server      default $OBSIDIAN_SERVER or ws://localhost:8080/ws/firewall
  --token-file  file holding the agent token from POST /api/v1/agents
                (default $OBSIDIAN_TOKEN_FILE or /etc/obsidian/agent.token)
  --hosts-file  default /etc/hosts; with --dry-run, $TMPDIR/obsidian-hosts
  --dry-run     print iptables commands instead of running them (no root needed)";

#[derive(Debug, PartialEq, Eq)]
pub struct Config {
    pub server: String,
    pub token: String,
    pub hosts_file: PathBuf,
    pub dry_run: bool,
}

pub fn from_args(args: impl IntoIterator<Item = String>) -> Result<Config, String> {
    let mut server = std::env::var("OBSIDIAN_SERVER").unwrap_or_else(|_| "ws://localhost:8080/ws/firewall".into());
    let mut token_file: PathBuf =
        std::env::var("OBSIDIAN_TOKEN_FILE").unwrap_or_else(|_| "/etc/obsidian/agent.token".into()).into();
    let mut hosts_file: Option<PathBuf> = None;
    let mut dry_run = false;

    let mut args = args.into_iter();
    while let Some(arg) = args.next() {
        let mut value = |name: &str| args.next().ok_or_else(|| format!("{name} needs a value\n\n{USAGE}"));
        match arg.as_str() {
            "--server" => server = value("--server")?,
            "--token-file" => token_file = value("--token-file")?.into(),
            "--hosts-file" => hosts_file = Some(value("--hosts-file")?.into()),
            "--dry-run" => dry_run = true,
            "-h" | "--help" => return Err(USAGE.into()),
            other => return Err(format!("unknown argument {other:?}\n\n{USAGE}")),
        }
    }
    if !(server.starts_with("ws://") || server.starts_with("wss://")) {
        return Err(format!("--server must be a ws:// or wss:// URL, got {server:?}"));
    }
    let token = read_token(&token_file)?;
    let hosts_file = hosts_file.unwrap_or_else(|| {
        if dry_run { std::env::temp_dir().join("obsidian-hosts") } else { PathBuf::from("/etc/hosts") }
    });
    Ok(Config { server, token, hosts_file, dry_run })
}

fn read_token(path: &PathBuf) -> Result<String, String> {
    let token = fs::read_to_string(path)
        .map_err(|e| format!("can't read the agent token from {}: {e}", path.display()))?
        .trim()
        .to_string();
    let well_formed = token.len() == 68
        && token.starts_with("obs_")
        && token[4..].bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b));
    if !well_formed {
        return Err(format!("{} doesn't hold an agent token (obs_ + 64 hex characters)", path.display()));
    }
    if let Ok(meta) = fs::metadata(path) {
        if meta.permissions().mode() & 0o077 != 0 {
            eprintln!("obsidian: warning: {} is readable by other users; chmod 600 it", path.display());
        }
    }
    Ok(token)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn token_file(content: &str) -> (tempfile::TempDir, String) {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("agent.token");
        fs::write(&path, content).unwrap();
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600)).unwrap();
        (dir, path.to_string_lossy().into_owned())
    }

    fn args(list: &[&str]) -> Vec<String> {
        list.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn reads_flags_and_the_token_file() {
        let token = format!("obs_{}", "ab".repeat(32));
        let (_dir, path) = token_file(&format!("{token}\n"));

        let config = from_args(args(&["--server", "wss://obsidian.example/ws/firewall", "--token-file", &path,
            "--hosts-file", "/tmp/h", "--dry-run"])).unwrap();

        assert_eq!(config, Config { server: "wss://obsidian.example/ws/firewall".into(), token,
            hosts_file: "/tmp/h".into(), dry_run: true });
    }

    #[test]
    fn rejects_a_malformed_token_and_a_non_websocket_server() {
        let (_dir, path) = token_file("not-a-token");
        assert!(from_args(args(&["--token-file", &path])).unwrap_err().contains("agent token"));

        let (_dir2, good) = token_file(&format!("obs_{}", "0".repeat(64)));
        assert!(from_args(args(&["--server", "http://x/ws/firewall", "--token-file", &good])).is_err());
        assert!(from_args(args(&["--bogus"])).is_err());
    }
}
