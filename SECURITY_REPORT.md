# Security Audit Report - T9 Keyboard (THREAT-001)

This report details the security and privacy audit of the T9 Keyboard IME codebase.

## Finding 1: Sensitive Data Leakage in Learned Dictionary (THREAT-001)
*   **Rating**: High
*   **Description**: The keyboard was previously learning and storing words typed into sensitive fields, such as password inputs, numeric-only fields (often used for PINs), and phone number fields. This data was persisted in the `learned_words` SharedPreferences file, potentially exposing sensitive user credentials.
*   **Recommended Fix**: Implement a suppression mechanism that checks the `EditorInfo.inputType` of the active field. Suppress both word learning and suggestion generation if the field is a password, numeric, or phone type.
*   **Status**: ✅ **Enhanced**. Implemented `T9Utils.isInputTypeSensitive` checking password/number/phone fields. Recently added hint/label keyword detection for "password", "pin", "credit", "card", "cvv", "ssn", "social", "secret". Integrated checks in `T9InputMethodService`, `SuggestionEngine`, and added app blacklist check before word learning.

## Finding 2: Excessive Data Retention for Learned Words (THREAT-002)
*   **Rating**: Medium
*   **Description**: Words added to the `LearnedDictionary` with a frequency greater than 1 were stored indefinitely. While a recency decay multiplier was applied for ranking, the raw words remained on disk. This poses a long-term privacy risk if a user types sensitive unique information multiple times (e.g., specific addresses or account numbers).
*   **Recommended Fix**: Implement a hard expiration limit for all entries in the learned dictionary.
*   **Status**: ✅ **Fixed**. Updated `LearnedDictionary.cleanup` to enforce a hard 180-day (6-month) expiration for all words, regardless of frequency.

## Finding 3: Contacts Permission Usage Audit (THREAT-003)
*   **Rating**: Low
*   **Description**: `ContactsDictionary` requires the `READ_CONTACTS` permission. While it correctly checks for permission at runtime, the tokenization logic could potentially ingest sensitive contact info if not carefully scoped.
*   **Analysis**: The current implementation only reads `DISPLAY_NAME_PRIMARY` and splits it into individual word tokens. It limits tokens to 2000 and filters out non-alpha words. This is a safe and localized usage of contact data for improving multi-tap/XT9 suggestions.
*   **Recommended Fix**: Ensure the user can easily toggle this feature off in settings.
*   **Status**: ✅ **Verified**. The feature is opt-in and can be disabled in the Settings UI, which clears the memory buffer.

## Finding 4: External Intent Exposure (THREAT-004)
*   **Rating**: Low
*   **Description**: Potential for internal data exposure via implicit intents.
*   **Analysis**: A review of `T9InputMethodService` and `MainActivity` shows that internal transitions use explicit intents (targeting `SettingsActivity.class`). The only semi-implicit intent is for the system clipboard manager, which is standard and doesn't expose internal private data.
*   **Status**: ✅ **Safe**.

## Finding 5: Telemetry File Permissions (CrashLogger) (THREAT-005)
*   **Rating**: Low
*   **Description**: The `CrashLogger` writes error logs to `t9_errors.log` in `filesDir`. There was a concern that these logs might be world-readable.
*   **Analysis**: `CrashLogger` uses the standard `File` API within the app's internal storage (`context.filesDir`). On Android, this directory is private to the application (UID-based isolation), which is equivalent to `MODE_PRIVATE`. No `MODE_WORLD_READABLE` or `MODE_WORLD_WRITEABLE` flags are used.
*   **Status**: ✅ **Enhanced**. File permissions are secure (private to app via UID isolation). Recently updated to sanitize log messages using `Regex("\\b[a-z]{6,}\\b")` to replace lowercase natural language words (6+ chars) with `***`, while preserving class names, method names, and file paths that contain mixed case/digits/dots.

## Finding 6: Stale Learned Word Persistence (THREAT-006)
*   **Rating**: Medium
*   **Description**: The decay logic in `LearnedDictionary` was not enforcing expiration at read-time, and the forgetting mechanism only targeted low-frequency words. This allowed stale learned words (even highly frequent ones) to persist and be suggested indefinitely.
*   **Recommended Fix**: Enforce 180-day hard expiration at both read-time (filtering in suggestions) and write-time (cleanup).
*   **Status**: ✅ **Fixed**. Updated `LearnedDictionary.getSuggestions`, `getNextWordSuggestions`, and `cleanup` to enforce the 180-day limit.

## Finding 7: Unencrypted Data Storage (THREAT-007)
*   **Rating**: High
*   **Description**: Both `LearnedDictionary` (`learned_words.xml`) and `PreferencesManager` (`t9_prefs.xml`) were storing data in plain text via `SharedPreferences`. This posed a risk if device is compromised.
*   **Recommended Fix**: Encrypt sensitive SharedPreferences using a modern, hardware-backed encryption library.
*   **Status**: ✅ **Fixed**. Migrated both `LearnedDictionary` and `PreferencesManager` to use [SafeBox](https://github.com/harrytmthy/safebox) (ChaCha20-Poly1305 encryption). Includes one-time automatic migration from plain SharedPreferences to SafeBox on first launch.

## Finding 8: Sensitive Data in Backups (THREAT-008)
*   **Rating**: High
*   **Description**: Android Auto-Backup could include `learned_words.xml` and `t9_prefs.xml` in cloud backups and device-to-device transfers, potentially exposing user typing history and settings.
*   **Recommended Fix**: Configure `backup_rules.xml` and `data_extraction_rules.xml` to exclude sensitive SharedPreferences from backups.
*   **Status**: ✅ **Fixed**. Configured `backup_rules.xml` (Android 11-) and `data_extraction_rules.xml` (Android 12+) to exclude `learned_words.xml` and `t9_prefs.xml` from both cloud backup and device transfer. Set `disableIfNoEncryptionCapabilities="true"` on cloud-backup to prevent unencrypted backups.

## Finding 9: No Per-App Learning Controls (THREAT-009)
*   **Rating**: Medium
*   **Description**: Users had no way to prevent the keyboard from learning words typed in specific apps (e.g., banking apps, secure messengers).
*   **Recommended Fix**: Add a user-configurable app blacklist in Settings to disable word learning on a per-app basis.
*   **Status**: ✅ **Fixed**. Created `AppBlacklistActivity` with RecyclerView + DiffUtil for efficient toggling. Added blacklist storage in `PreferencesManager` using SafeBox. Integrated check in `T9InputMethodService.learnWord()` methods using `currentInputBinding?.packageName`. Accessible via Settings > Privacy > App Blacklist.

---
*Original audit performed by Jules on April 3, 2026.*
*Updated with security hardening implementation on May 5, 2026.*
