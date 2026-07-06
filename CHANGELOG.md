## [Unreleased]
### Added
- New 5-column × 2-letters-per-key layout defined in `KeyboardLayout.kt` (single source of truth for labels, hints, multi-tap characters, and engine key codes).
- Key-preview popup: an accent-filled rounded square appears above the touched letter key.
- Long-press outputs matching each key's corner hint: digits 1–9 and 0, `*` (ST), `#` (UV), `/` (YZ); WX long-press opens the number page; Enter long-press opens the emoji picker; SYM long-press switches keyboards.
- Enter key performs the editor's `IME_ACTION_*` (Search/Send/Go…) when one is set.
- `KeyboardLayoutTest` covering grid shape, letter coverage, and key-code mapping invariants.
- Created `tools/raw/` for raw dictionary processing files.

### Removed
- Unused public functions: `AospDictionary.isValidWord()`, `LearnedDictionary.isValidWord()`, `LearnedDictionary.contains()`, and `ContactsDictionary.isEmpty()`.
- Autocorrect remnants: `SettingsDialogHelper.showSensitivityDialog()` and related preference keys (`KEY_AUTOCORRECT_ENABLED`, `KEY_AUTOCORRECT_SENSITIVITY`).
- Orphaned drawables: `toggle_on.xml`, `toggle_off.xml`, `ic_arrow_alt_from_left.xml`, `ic_arrow_alt_from_right.xml`, `key_background_active.xml`.
- Unused menu: `settings_menu.xml`.
- Orphaned string keys: `haptic_feedback`, `haptic_intensity`, `key_press_sound`, `key_press_volume`, `multi_tap_timeout`, `key_label_font_size`, `suggestion_font_size`, `clear_dictionary`.
- Raw dictionary asset `main_en_US.combined.txt` (moved to `tools/raw/`).

### Fixed
- Gated `ContactsDictionary` debug log with `BuildConfig.DEBUG` to prevent leaking internal state in production builds.
- Updated `LearnedDictionaryTest.kt` to remove calls to deleted functions.

### Changed
- Visual redesign ("Obsidian Flux"): `#1A1A1A` keyboard background, `#0D0D0D` key surfaces with a 1dp 5%-white hairline, 16dp corner radius, 6dp grid gap, 8dp container margin; pressed keys flash `#2979FF` and scale to 0.95; backspace and active shift fill with the accent color (`#558DFF` default) with white content.
- Prediction engine (AOSP/Learned/Contacts dictionaries) reindexed for the 2-letter key pairs; key codes extend beyond digits to `*`, `#`, `+`, `/`.
- Enter key sub icon replaced with accent-colored dot indicator
