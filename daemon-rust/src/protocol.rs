//! /ws/firewall messages; mirrors docs/protocol.md.

use serde::Deserialize;

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct RuleEntry {
    #[serde(rename = "ruleId")]
    pub rule_id: String,
    pub indicator_type: String,
    pub value: String,
}

#[derive(Debug, PartialEq, Eq, Deserialize)]
#[serde(tag = "type")]
pub enum ServerMessage {
    #[serde(rename = "SYNC")]
    Sync { rules: Vec<RuleEntry> },
    #[serde(rename = "BLOCK")]
    Block(RuleEntry),
    #[serde(rename = "BYPASS")]
    Bypass(RuleEntry),
    /// Types this daemon doesn't know are ignored, as the protocol requires.
    #[serde(other)]
    Unknown,
}

pub const HEARTBEAT: &str = r#"{"type":"HEARTBEAT"}"#;

#[cfg(test)]
mod tests {
    use super::*;

    fn entry(kind: &str, value: &str) -> RuleEntry {
        RuleEntry { rule_id: "4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a01".into(), indicator_type: kind.into(), value: value.into() }
    }

    #[test]
    fn parses_the_protocol_examples() {
        let sync: ServerMessage = serde_json::from_str(
            r#"{"type":"SYNC","rules":[{"ruleId":"4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a01","indicator_type":"DOMAIN","value":"testsafebrowsing.appspot.com"}]}"#,
        )
        .unwrap();
        assert_eq!(sync, ServerMessage::Sync { rules: vec![entry("DOMAIN", "testsafebrowsing.appspot.com")] });

        let block: ServerMessage = serde_json::from_str(
            r#"{"type":"BLOCK","ruleId":"4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a01","indicator_type":"IP","value":"203.0.113.45"}"#,
        )
        .unwrap();
        assert_eq!(block, ServerMessage::Block(entry("IP", "203.0.113.45")));

        let bypass: ServerMessage = serde_json::from_str(
            r#"{"type":"BYPASS","ruleId":"4a0e5c7f-31d2-4b8e-9f60-1c2d3e4f5a01","indicator_type":"IP","value":"203.0.113.45","at":"2026-10-27T10:15:00Z"}"#,
        )
        .unwrap();
        assert_eq!(bypass, ServerMessage::Bypass(entry("IP", "203.0.113.45")));
    }

    #[test]
    fn unknown_types_are_ignored() {
        let message: ServerMessage = serde_json::from_str(r#"{"type":"ACK_REQUEST","x":1}"#).unwrap();
        assert_eq!(message, ServerMessage::Unknown);
    }

    #[test]
    fn heartbeat_is_the_documented_frame() {
        let value: serde_json::Value = serde_json::from_str(HEARTBEAT).unwrap();
        assert_eq!(value, serde_json::json!({"type": "HEARTBEAT"}));
    }
}
