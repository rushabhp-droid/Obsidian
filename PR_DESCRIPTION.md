# Pull Request: Implement Obsidian Android Mobile App with Stitch Material 3 UI (Module 3)

## 📌 Summary

This PR implements **Module 3 (Native Android Mobile App)** from the master plan (`TODO.md`), delivering zero-jargon mobile threat defense for regular consumers and enterprise employees. The UI strictly adheres to the official Google Stitch design system ([`projects/6036591480365689582`](https://stitch.withgoogle.com/projects/6036591480365689582)).

---

## 🎨 UI & Design Implementation (Stitch Android Final)

Implemented all 9 screens using Jetpack Compose with strict adherence to Stitch tokens:
- **Strict Shape Policy**: All buttons, chips, segmented tabs, and navigation indicators use **`8dp`** corner radius (**never pill / rounded-full**). Cards use **`12dp`**.
- **Palette**: Light theme seed `#00696D` (`Primary` `#00696D`, `PrimaryContainer` `#6FF6FC`, `Surface` `#FAFDFD`, `SurfaceVariant` `#DAE4E5`).
- **Typography**: `Roboto Flex` for interface text, `JetBrains Mono` for URLs, domains, phone numbers, and hashes.
- **Color Semantics**:
  - **Scam**: Error `#BA1A1A` / ErrorContainer `#FFEDEA`
  - **Suspicious**: Tertiary `#7D5700` / TertiaryContainer `#FFDEA0`
  - **Checking & No threats found**: OnSurfaceVariant `#3F4849` / SurfaceVariant `#DAE4E5` (**Never green**).

### Screens Delivered:
1. **`01 Lock (Light)`** (`LockScreen.kt`): Biometric fingerprint prompt (`BiometricPrompt`) with device credential/PIN fallback.
2. **`02 Home Setup (Light)`** (`HomeSetupScreen.kt`): Onboarding and permission gate when notification access is disabled.
3. **`03 Home (Light)`** (`HomeScreen.kt`): Active shield dashboard with live threat badges and recent checks list.
4. **`04 Messages (Light)`** (`MessagesScreen.kt`): Full threat triage feed with search and filter chips (`All`, `Scam`, `Suspicious`, `Checking`).
5. **`05 Message check (Light)`** (`MessageCheckDetailScreen.kt`): Threat card, privacy guarantee notice, and 1-tap **"Report as scam"** / **"I trust this link"** buttons.
6. **`06 Report confirmed (Light)`** (`ReportConfirmedScreen.kt`): Block confirmation banner and undo capability.
7. **`07 Rules Blocked (Light)`** (`RulesScreen.kt`): Blacklist manager with indicator type icons and delete actions.
8. **`08 Rules Trusted (Light)`** (`RulesScreen.kt`): Whitelist manager supporting time-bound TTL overrides (`Expires in 15 min`).
9. **`09 Settings (Light)`** (`SettingsScreen.kt`): Protection switches, OLED pure black mode, and account controls.
10. **`Add Rule Dialog`** (`AddRuleDialog.kt`): Dialog to manually add threat rules with TTL duration selectors.

---

## 🛡️ Core Security Architecture

1. **SMS & WhatsApp Interception** (`MessagingNotificationService.kt`):
   - Intercepts incoming notifications for WhatsApp, WhatsApp Business, and default SMS providers.
   - Extracts individual messages from `MessagingStyle` extras (`EXTRA_MESSAGES`), avoiding dropped messages from grouped summaries.
   - Bounded deduplication cache prevents repeated scanning of re-posted WhatsApp notifications.
2. **Privacy-Preserving Contact List Triage** (`ContactResolver.kt`):
   - **Saved Contacts**: Conversational text is stripped completely for user privacy; only URLs are sent for verification (`isSavedContact = true`, `text = null`).
   - **Unknown Senders**: Full message body + URLs are evaluated (`isSavedContact = false`) to catch social engineering / urgent wire transfer patterns.
3. **SIM Change Detection [P1]** (`SimChangeMonitor.kt`):
   - Calculates SHA-256 fingerprint of `(carrierId + mccString + mncString + simSlotIndex + subscriptionId)`.
   - On physical SIM removal or swap: invalidates session tokens, locks the app, and triggers high-priority alert.
4. **Rule Precedence Engine** (`ThreatScanner.kt`):
   - Evaluates active Whitelist -> Blacklist -> `/api/v1/scan/unified` VirusTotal v3 and Heuristics.

---

## 🧪 Testing

- **`UrlExtractorTest.kt`**: Tests scheme & schemeless URLs, query strings, and domain normalization.
- **`SimFingerprintTest.kt`**: Tests SHA-256 fingerprint consistency and change detection.
- **`RulePrecedenceTest.kt`**: Validates 6-tier rule precedence and TTL expiry.
- **Interactive Web Simulator & Mock REST API** (`web-simulator/`): Includes zero-dependency Node server providing instant preview at `http://localhost:3300`.

---

## 📋 Checklist

- [x] Code compiles cleanly with Kotlin 2.0 / Compose BOM.
- [x] Strict adherence to 8dp shape rules and Stitch Material 3 tokens.
- [x] All 9 Stitch screens implemented and functional.
- [x] Privacy contact triage verified (saved contacts strip message body).
- [x] SIM change detection verified with SHA-256 fingerprinting.
- [x] Unit test suite added and passing.
- [x] Updated `TODO.md` Dev 3 tasks to complete.
