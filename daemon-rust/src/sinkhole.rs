//! /etc/hosts DNS sinkhole: `0.0.0.0 <domain> # OBSIDIAN-MANAGED` lines, written
//! to a temp file in the same directory and renamed over the original.

use std::collections::BTreeSet;
use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};

pub const MARKER: &str = "# OBSIDIAN-MANAGED";

/// The hosts file with every managed line replaced by one line per domain.
pub fn render(original: &str, domains: &BTreeSet<String>) -> String {
    let mut out = String::new();
    for line in original.lines().filter(|line| !line.trim_end().ends_with(MARKER)) {
        out.push_str(line);
        out.push('\n');
    }
    for domain in domains {
        out.push_str(&format!("0.0.0.0 {domain} {MARKER}\n"));
    }
    out
}

pub struct Sinkhole {
    path: PathBuf,
}

impl Sinkhole {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Sinkhole { path: path.into() }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn write(&self, domains: &BTreeSet<String>) -> io::Result<()> {
        let original = match fs::read_to_string(&self.path) {
            Ok(text) => text,
            Err(e) if e.kind() == io::ErrorKind::NotFound => String::new(),
            Err(e) => return Err(e),
        };
        let mode = fs::metadata(&self.path).map(|m| m.permissions().mode() & 0o777).unwrap_or(0o644);
        let dir = self.path.parent().filter(|p| !p.as_os_str().is_empty()).unwrap_or(Path::new("."));
        let name = self.path.file_name().and_then(|n| n.to_str()).unwrap_or("hosts");
        let temp = dir.join(format!(".{name}.obsidian.tmp"));
        {
            let mut file: File =
                OpenOptions::new().write(true).create(true).truncate(true).mode(mode).open(&temp)?;
            file.write_all(render(&original, domains).as_bytes())?;
            file.sync_all()?;
        }
        fs::set_permissions(&temp, fs::Permissions::from_mode(mode))?;
        fs::rename(&temp, &self.path)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn set(values: &[&str]) -> BTreeSet<String> {
        values.iter().map(|v| v.to_string()).collect()
    }

    #[test]
    fn keeps_the_users_lines_and_replaces_only_managed_ones() {
        let hosts = "127.0.0.1 localhost\n0.0.0.0 old.example # OBSIDIAN-MANAGED\n::1 localhost\n";
        assert_eq!(
            render(hosts, &set(&["evil.example"])),
            "127.0.0.1 localhost\n::1 localhost\n0.0.0.0 evil.example # OBSIDIAN-MANAGED\n"
        );
    }

    #[test]
    fn empty_set_removes_every_managed_line_and_is_idempotent() {
        let hosts = "127.0.0.1 localhost\n0.0.0.0 a.example # OBSIDIAN-MANAGED\n";
        let once = render(hosts, &BTreeSet::new());
        assert_eq!(once, "127.0.0.1 localhost\n");
        assert_eq!(render(&once, &BTreeSet::new()), once);
    }

    #[test]
    fn write_replaces_the_file_atomically_and_keeps_its_mode() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("hosts");
        fs::write(&path, "127.0.0.1 localhost\n").unwrap();
        fs::set_permissions(&path, fs::Permissions::from_mode(0o644)).unwrap();
        let sinkhole = Sinkhole::new(&path);

        sinkhole.write(&set(&["b.example", "a.example"])).unwrap();

        assert_eq!(
            fs::read_to_string(&path).unwrap(),
            "127.0.0.1 localhost\n0.0.0.0 a.example # OBSIDIAN-MANAGED\n0.0.0.0 b.example # OBSIDIAN-MANAGED\n"
        );
        assert_eq!(fs::metadata(&path).unwrap().permissions().mode() & 0o777, 0o644);
        assert!(!dir.path().join(".hosts.obsidian.tmp").exists());
    }
}
