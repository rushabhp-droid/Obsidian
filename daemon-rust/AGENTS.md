# Daemon instructions

- Validate every IP and domain before any system call. Invoke commands with `Command::new(...).args(...)`; never use a shell.
- Put firewall changes only in the dedicated `OBSIDIAN` chain. Cleanup must remove the chain's rules safely.
- Write only `0.0.0.0 <domain> # OBSIDIAN-MANAGED` entries to the managed hosts section. Use a temporary file and atomic rename, then flush DNS.
- Reconcile the full server `SYNC` after each connection, heartbeat every 10 seconds, and remove managed state on SIGINT/SIGTERM.
- Preserve `--dry-run` behavior so tests and local runs never modify the host firewall or `/etc/hosts`.
- Run daemon checks with `cargo test`.
