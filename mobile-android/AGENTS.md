# Android instructions

- This is the B2C app. It talks only to `/api/b2c/v1` with `obc_` bearer tokens (`../docs/protocol.md`, "B2C app API"); never call the dashboard's `/api/v1`. The B2B scope gates in `../TODO.md` don't apply to it.
- UI is Jetpack Compose with Material 3. Colours come from roles in `theme/Theme.kt` (error = dangerous, tertiary = suspicious, surfaceVariant = checking or no threats found); never show anything green or "safe" for an unknown verdict.
- Unknown is "Checking" (`PENDING`), never "No threats found". A failed or slow check holds a link; it is never passed on as safe.
- Notification auditor: read individual messages from `MessagingStyle`, not summary text, and dedupe per message (WhatsApp reposts the conversation). Watch `com.whatsapp`, `com.whatsapp.w4b`, and the user's default SMS package only.
- For saved contacts, send URLs only; message text must stay on the phone. For unknown senders, text and URLs may be sent. Without `READ_CONTACTS`, treat every sender as saved (fail closed).
- Notifications carry no attachments, so the auditor never sends file hashes. The Home file analyzer hashes a file the user picked and sends only the SHA-256; never upload the file.
- The firewall is a DNS-only `VpnService`; other traffic must not pass through the app. Its request log stays in memory on the phone.
- The app lock always has the PIN; fingerprint is optional on top. Store the PIN only as the PBKDF2 hash in `SessionStore` (encrypted preferences, no unencrypted fallback).
- Do not log bearer tokens, codes, PINs or message text. The server address is set in the app (debug builds default to `http://10.0.2.2:8080`); cleartext HTTP is allowed only for local hosts (`res/xml/network_security_config.xml`).
- Call the feature "SIM change detection." It is [P1] and not wired into the app.
- Run Android checks with `scripts/build.sh android` (tests, then debug and release APKs in `../dist/`; it picks a JDK 17–23 and the SDK itself), or `./gradlew assembleDebug testDebugUnitTest` with `JAVA_HOME` set to JDK 21. The wrapper's Gradle doesn't run on JDK 24+; don't use a system Gradle 9.
- Release signing and the release APK's starting server come only from environment variables (`OBSIDIAN_ANDROID_KEYSTORE*`, `OBSIDIAN_SERVER_URL`); never commit a keystore or its passwords.
