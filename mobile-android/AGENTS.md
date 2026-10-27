# Android instructions

- Read individual messages from `MessagingStyle` `EXTRA_MESSAGES`, not notification summary text. Dedupe reposted notifications.
- Watch `com.whatsapp`, `com.whatsapp.w4b`, and the user's default SMS package.
- For saved contacts, send URLs only; message text must stay on the phone. For unknown senders, text and URLs may be sent for analysis.
- Resolve WhatsApp display names through `ContactsContract`. Notifications do not provide attachments, so never send file hashes.
- Use the shared request and response shapes in `../docs/protocol.md`. The open Android/backend gaps are tracked in the P0 section of `../TODO.md`.
- Do not log bearer tokens or message text in release builds. Use configurable API origins rather than a hard-coded public hostname.
- Call the feature “SIM change detection.”
