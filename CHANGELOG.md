## [Unreleased]
### Added
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
- Enter key sub icon replaced with accent-colored dot indicator
