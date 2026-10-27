//! Obsidian endpoint firewall daemon (Linux). Connects to /ws/firewall with its
//! agent token, applies SYNC / BLOCK / BYPASS to the OBSIDIAN iptables chain and
//! the /etc/hosts sinkhole, heartbeats every 10 s, reconnects with backoff, and
//! removes everything it added on SIGINT / SIGTERM.

mod config;
mod firewall;
mod protocol;
mod reconcile;
mod sinkhole;
mod validate;

use futures_util::{SinkExt, StreamExt};
use protocol::ServerMessage;
use reconcile::Enforcer;
use std::process::ExitCode;
use std::time::{Duration, Instant};
use tokio::signal::unix::{signal, SignalKind};
use tokio_tungstenite::tungstenite::client::IntoClientRequest;
use tokio_tungstenite::tungstenite::http::{header::AUTHORIZATION, HeaderValue};
use tokio_tungstenite::tungstenite::{Error as WsError, Message};

const HEARTBEAT_EVERY: Duration = Duration::from_secs(10);
const MAX_BACKOFF: Duration = Duration::from_secs(30);

#[tokio::main(flavor = "current_thread")]
async fn main() -> ExitCode {
    let config = match config::from_args(std::env::args().skip(1)) {
        Ok(config) => config,
        Err(message) => {
            eprintln!("{message}");
            return ExitCode::from(2);
        }
    };
    let runner: Box<dyn firewall::Runner + Send> =
        if config.dry_run { Box::new(firewall::DryRunner) } else { Box::new(firewall::SystemRunner) };
    let mut enforcer = Enforcer::new(runner, sinkhole::Sinkhole::new(&config.hosts_file));
    enforcer.prepare();
    eprintln!("obsidian: sinkhole file {}{}", config.hosts_file.display(), if config.dry_run { " (dry run)" } else { "" });

    let shutdown = shutdown_signal();
    tokio::pin!(shutdown);
    let mut backoff = Duration::from_secs(1);
    loop {
        tokio::select! {
            _ = &mut shutdown => break,
            result = session(&config, &mut enforcer) => match result {
                Ok(()) => {
                    eprintln!("obsidian: disconnected; keeping the current blocks until the next SYNC");
                    backoff = Duration::from_secs(1);
                }
                Err(reason) => eprintln!("obsidian: {reason}; retrying in {}s", backoff.as_secs()),
            },
        }
        tokio::select! {
            _ = &mut shutdown => break,
            _ = tokio::time::sleep(backoff) => {}
        }
        backoff = (backoff * 2).min(MAX_BACKOFF);
    }
    enforcer.cleanup();
    eprintln!("obsidian: stopped; OBSIDIAN chain flushed and managed hosts lines removed");
    ExitCode::SUCCESS
}

/// One connection: SYNC arrives first, then live changes; heartbeats go out every 10 s.
async fn session(config: &config::Config, enforcer: &mut Enforcer) -> Result<(), String> {
    let mut request = config.server.as_str().into_client_request().map_err(|e| format!("bad server URL: {e}"))?;
    let bearer = HeaderValue::from_str(&format!("Bearer {}", config.token)).map_err(|e| e.to_string())?;
    request.headers_mut().insert(AUTHORIZATION, bearer);
    let (socket, _) = tokio_tungstenite::connect_async(request).await.map_err(|e| match e {
        WsError::Http(response) if response.status().as_u16() == 401 => {
            "the server refused the agent token (401); register the agent again".to_string()
        }
        other => format!("can't connect to {}: {other}", config.server),
    })?;
    eprintln!("obsidian: connected to {}", config.server);
    let (mut outgoing, mut incoming) = socket.split();
    let mut heartbeat = tokio::time::interval_at(tokio::time::Instant::now() + HEARTBEAT_EVERY, HEARTBEAT_EVERY);
    loop {
        tokio::select! {
            _ = heartbeat.tick() => outgoing
                .send(Message::text(protocol::HEARTBEAT))
                .await
                .map_err(|e| format!("heartbeat failed: {e}"))?,
            frame = incoming.next() => match frame {
                None | Some(Ok(Message::Close(_))) => return Ok(()),
                Some(Err(e)) => return Err(format!("connection lost: {e}")),
                Some(Ok(Message::Text(text))) => handle(enforcer, text.as_str()),
                Some(Ok(_)) => {}
            },
        }
    }
}

fn handle(enforcer: &mut Enforcer, text: &str) {
    let started = Instant::now();
    match serde_json::from_str::<ServerMessage>(text) {
        Ok(ServerMessage::Sync { rules }) => {
            let (applied, rejected) = enforcer.sync(&rules);
            let (ips, domains) = enforcer.blocked();
            eprintln!("obsidian: SYNC applied {applied} ({ips} IPs, {domains} domains, {rejected} rejected) in {} ms",
                started.elapsed().as_millis());
        }
        Ok(ServerMessage::Block(rule)) => report("BLOCK", &rule, enforcer.block(&rule), started),
        Ok(ServerMessage::Bypass(rule)) => report("BYPASS", &rule, enforcer.bypass(&rule), started),
        Ok(ServerMessage::Unknown) => {}
        Err(e) => eprintln!("obsidian: ignoring a frame that isn't a protocol message: {e}"),
    }
}

fn report(kind: &str, rule: &protocol::RuleEntry, result: Result<(), String>, started: Instant) {
    match result {
        Ok(()) => eprintln!("obsidian: {kind} {} {} applied in {} ms", rule.indicator_type, rule.value,
            started.elapsed().as_millis()),
        Err(reason) => eprintln!("obsidian: refused {kind} {}: {reason}", rule.rule_id),
    }
}

async fn shutdown_signal() {
    let mut terminate = signal(SignalKind::terminate()).expect("SIGTERM handler");
    tokio::select! {
        _ = tokio::signal::ctrl_c() => {}
        _ = terminate.recv() => {}
    }
}
