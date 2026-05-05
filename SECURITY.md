# Security Documentation - T9 Keyboard

This document outlines the security measures implemented in the T9 Keyboard application to protect user data.

## Data Encryption

All sensitive user data stored on device is encrypted at rest using [SafeBox](https://github.com/harrytmthy/safebox), which provides ChaCha20-Poly1305 encryption for stored preferences.

### Encrypted Data
- **Learned Dictionary** (`learned_words`): Stores user-typed words for prediction improvement. Encrypted with SafeBox using hardware-backed keystore.
- **App Preferences** (`t9_prefs`): Contains user settings including sensitivity toggles. Encrypted with SafeBox.

## Backup Exclusions

Sensitive data is excluded from Android Auto-Backup and device-to-device transfers:

- `learned_words.xml` - Excluded from both cloud backup and device transfer
- `t9_prefs.xml` - Excluded from both cloud backup and device transfer

Cloud backups require encryption capabilities (`disableIfNoEncryptionCapabilities="true"`). If the device does not support backup encryption, no backup occurs.

## Sensitive Input Detection

The keyboard detects sensitive input fields and suppresses word learning/suggestions:

- **Password fields**: `TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`
- **Numeric fields**: `TYPE_CLASS_NUMBER` (often used for PINs)
- **Phone fields**: `TYPE_CLASS_PHONE`
- **Field hints/labels**: Checks for keywords like "password", "pin", "credit", "card", "cvv", "ssn", "social", "secret"

When a sensitive field is detected, the keyboard:
- Does not learn typed words
- Does not generate suggestions from learned dictionary
- Only provides system dictionary suggestions

## App Blacklist

Users can configure a blacklist of apps in Settings > Privacy > App Blacklist. When an app is blacklisted:
- Word learning is disabled while typing in that app
- `learnWord()` is silently skipped for that app's input sessions
- The current app package is read from `currentInputBinding?.packageName`

## Crash Logging

Crash logs are written to internal storage (`t9_errors.log`) which is private to the app (UID-based isolation).

- Only exception class names and sanitized messages are logged (not full stack traces)
- Messages with sequences of lowercase natural language words (6+ characters) are replaced with `***`
- Class names, method names, and file paths contain mixed case, digits, and dots so they are not affected
- No user-typed content or contact info is included in logs
- Logs are capped at 200 lines with automatic rotation
