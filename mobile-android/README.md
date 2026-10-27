# Obsidian Mobile - Cybersecurity App

**Real-Time Threat Defense for Android & Mobile**
Built according to the Obsidian Hackathon Master Plan (`TODO.md`) and designed to match the Google Stitch Design System (`projects/6036591480365689582`).

---

## 🛡️ Overview

Obsidian Mobile is the native consumer and enterprise endpoint defense application that intercepts incoming SMS and WhatsApp messages in real time, extracts URLs, performs privacy-preserving contact triage, queries threat intelligence (VirusTotal API v3 + PostgreSQL Rule Precedence), alerts the user to malicious links, and enables 1-tap **Report** (blacklisting for the entire organization) or **Trust & Bypass** (temporary or permanent whitelisting).

---

## 📱 Stitch UI Specification Compliance

This application adheres strictly to the **Obsidian Android (Final)** design system:
- **Theme & Palette:** Seed `#00696D`, Material 3 Light scheme (`Primary`: `#00696D`, `PrimaryContainer`: `#6FF6FC`, `Surface`: `#FAFDFD`, `SurfaceVariant`: `#DAE4E5`).
- **Shape System (Strict):**
  - **All Buttons (filled, outlined, text, tonal):** `8dp` corner radius. **Never pill / rounded-full**.
  - **Chips, segmented buttons, badges, nav bar active indicator:** `8dp` corner radius.
  - **Cards & info rows:** `12dp` corner radius.
  - **Text fields:** `4dp` top corners.
- **Typography:**
  - UI labels and body: `Roboto Flex` (sans-serif).
  - URLs, domains, phone numbers, hashes: `JetBrains Mono` (monospace).
- **Color Semantics:**
  - **Scam:** error (`#BA1A1A`) / errorContainer (`#FFEDEA`) / onErrorContainer (`#410002`).
  - **Suspicious:** tertiary (`#7D5700`) / tertiaryContainer (`#FFDEA0`) / onTertiaryContainer (`#271900`).
  - **Checking & No threats found:** onSurfaceVariant (`#3F4849`) / surfaceVariant (`#DAE4E5`). **Never green**, as mandated by design theme.

---

## 🧩 Screen Implementations (Screens 01 – 09)

1. **`01 Lock (Light)` (`LockScreen.kt`)**: Hardware-grade biometric lock (`BiometricPrompt` with device credential/PIN fallback). Center lock icon, headlineSmall `Obsidian`, bodyMedium subtitle, `8dp` filled button with `fingerprint` icon.
2. **`02 Home Setup (Light)` (`HomeSetupScreen.kt`)**: Empty state when notification access is disabled. `12dp` card with `notifications_off`, "Turn on message checking" button, and "Learn what we access".
3. **`03 Home (Light)` (`HomeScreen.kt`)**: Live protection dashboard showing "Message checking is on", verified badge, 5 recent check items with threat badges (Scam, Suspicious, Checking, No threats found), and "See all messages" button.
4. **`04 Messages (Light)` (`MessagesScreen.kt`)**: Complete threat triage list with instant search bar, filter chips (`All`, `Scam`, `Suspicious`, `Checking`), date grouping (`Today`, `Yesterday`), channel indicator (WhatsApp vs SMS), and monospace domains.
5. **`05 Message check (Light)` (`MessageCheckDetailScreen.kt`)**: Deep-dive triage screen with dangerous threat banner ("This link is a known scam"), sender details, full URL, timestamp, security engine rationale, privacy guarantee callout, and 1-tap action buttons: **"Report as scam"** and **"I trust this link"**.
6. **`06 Report confirmed (Light)` (`ReportConfirmedScreen.kt`)**: Block confirmation banner ("This link is now blocked on this device and for your organization"), details card, and "Undo report" action.
7. **`07 Rules Blocked (Light)` (`RulesScreen.kt`)**: Blocked rules manager with `8dp` segmented tab switcher, indicator type icons (`link`, `phone`, `mail`), monospace values, source metadata, and delete action.
8. **`08 Rules Trusted (Light)` (`RulesScreen.kt`)**: Trusted whitelist manager with TTL support ("Expires in 15 min" with timer icon).
9. **`09 Settings (Light)` (`SettingsScreen.kt`)**: Protection toggles (Notification access, Lock with fingerprint), Appearance (Theme, High contrast OLED black mode), Account profile (`priya@example.com`), and About info.

---

## 🔒 Security & Architecture Features

### 1. Privacy-Preserving Contact List Triage (`ContactResolver.kt`)
- **Saved Contacts:** Plain conversational text is ignored completely for user privacy. URLs are extracted locally via regex; **only** the URLs are submitted to `/api/v1/scan/unified` with `isSavedContact = true` and `text = null`.
- **Unknown Senders:** If the sender is not in the user's contacts, the full message body + extracted URLs are sent with `isSavedContact = false` for urgency/social engineering heuristic analysis.

### 2. SIM Change Detection (`SimChangeMonitor.kt`) [P1]
- Monitors `SubscriptionManager` and `TelephonyManager`.
- Calculates SHA-256 fingerprint of `(carrierId + mccString + mncString + simSlotIndex + subscriptionId)`.
- Detects physical SIM removal, SIM swap, or service loss, instantly locking the session token and raising a high-priority alert.

### 3. Notification Interception Service (`MessagingNotificationService.kt`)
- Extends Android `NotificationListenerService` filtering `com.whatsapp`, `com.whatsapp.w4b`, and default SMS package.
- Extracts individual messages from `NotificationCompat.MessagingStyle` (`EXTRA_MESSAGES`), bypassing generic summary notifications ("3 new messages").
- Uses a bounded deduplication cache to handle duplicate notification posts.

### 4. Rule Precedence & Backend Synchronization (`ThreatScanner.kt`, `RuleRepository.kt`)
Matches the Master Plan rule precedence:
1. Local & Remote Whitelist (Trusted rules that haven't expired)
2. Local & Remote Blacklist (Blocked rules with organization crowd-immunity)
3. Backend `/api/v1/scan/unified` + VirusTotal API v3 + Heuristic Intelligence

### 5. Backend REST API Integration (`ApiClient.kt`)
- **Bearer Token Auth (`POST /api/v1/auth/login`)**: Mobile client authentication issuing `obm_...` bearer tokens stored in Android Keystore / encrypted storage.
- **Session Profile (`GET /api/v1/auth/me`)**: Retrieves user display name, email, and organization details (`Aster Capital`).
- **Telemetry History (`GET /api/v1/scans`)**: Synchronizes scan telemetry logs into the Messages triage feed.
- **Rules Synchronization (`GET /api/v1/rules`)**: Fetches active blacklist and whitelist entries.
- **Threat Reporting (`POST /api/v1/rules/report`)**: Upserts blacklisted domains to PostgreSQL and broadcasts to firewall daemons.
- **Whitelist Bypass (`POST /api/v1/rules/bypass`)**: Upserts trusted indicators with 15-minute TTL.
- **Rule Deletion (`DELETE /api/v1/rules/{id}`)**: Deletes rules with audit trail logging.
- **Configurable Control Plane**: Settings screen allows configuring backend endpoint (default: `http://10.0.2.2:8080`).

---

## 🚀 How to Run

### Option A: Interactive Web Simulator (Instant Preview)
A zero-dependency HTTP server and frontend simulator is provided to test the app right now in your browser:
```bash
cd mobile-android/web-simulator
node server.js
```
Open **`http://localhost:3300`** in any browser. The simulator includes:
- Live phone frame with pixel-perfect Stitch UI
- Interactive tab navigation and screen transitions
- Full REST API integration with real-time rule and scan synchronization
- Live Threat Scenario Generators:
  * WhatsApp Phishing (`secure-login-verify.co`)
  * Suspicious SMS (`bit.ly/3xQa9`)
  * Safe Contact Message (`youtube.com`)
  * SIM Change Alert (locks session)
  * Biometric Fingerprint lock/unlock trigger

### Option B: Android Native (Gradle / Studio)
Build and test with Gradle:
```bash
cd mobile-android
./gradlew testDebugUnitTest  # Run unit tests
./gradlew assembleDebug      # Build debug APK
```
Deploy the generated APK at `app/build/outputs/apk/debug/app-debug.apk` to an Android 8.0+ (API 26+) phone or emulator. Grant the **Notification Listener** permission when prompted to enable live WhatsApp and SMS interception.
