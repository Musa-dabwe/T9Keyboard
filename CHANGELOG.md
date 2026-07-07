## [Unreleased]
### Added
- Number pad: space key now reads `SPACE [0]` so the long-press zero is discoverable without the corner hint.
- Number pad: comma key shows a `-` corner hint; long-pressing it types a hyphen.
- Number pad: swiping down on the suggestion bar dismisses the pad back to the letters keyboard, making it behave like a separate panel (same gesture as the text-editing panel).
- Panel switches (letters ⇄ symbols/emoji/text-editing/number pad) animate with a short fade + upward slide.
- Dynamic key-preview popup transitions: pop-in with a slight overshoot on press, quick pulse when moving between keys, brief linger and fade-out on release.
- `AospDictionary.getSequenceForWord()`: the dictionary now exposes its word → key-combination mapping.
- `tools/normalize_dict.py`: normalizes the dictionary assets to lowercase (case duplicates merged).

### Changed
- Dictionary overhauled into a hashmap combination dictionary: every word is stored lowercase and mapped to the key sequence that types it, with a reverse sequence → words hashmap for O(1) exact T9 lookups (a sorted prefix index remains only for longer-word predictions). Stored capitalization is gone entirely.
- `en_us_words.txt` and `en_us_bigrams.txt` assets normalized to lowercase; 8,947 case-duplicate words (`As`/`as`, `On`/`on`, …) and 401 duplicate bigram pairs merged. The words asset remains the dictionary's load source; `tools/convert_dict.py` was updated to regenerate both assets in the new format.
- Capitalization now always follows the shift state. The only exemption is the pronoun `i` (plus its contractions `i'll`, `i'm`, …), which is always capitalized in XT9 mode; with XT9 off, multi-tap types a literal small `i`. Contact names keep their stored case as user data.
- Multi-tap suggestions follow the case of the composed text (which itself comes from the shift state).
- XT9 next-word suggestions are passed through the shift-state rule before display.
- Learned words are now always stored lowercase (one-time migration merges previously capitalized entries; contact names excepted).

### Removed
- Number pad: redundant long-press events on `@`, `1`-`9`, `*`, `#`, and the full-stop key (they duplicated the tap character).
- `ProperNounRegistry` and `res/raw/proper_nouns.txt`: stored-case preservation contradicted shift-state capitalization and could block the shift key from working on registered words.
- The multi-tap auto-conversion of a committed `i` to `I`.

### Fixed
- Words such as `as`, `on`, `of` no longer get spuriously capitalized: the old dictionary carried capitalized duplicates (`As` 176, `On` 168, …) and 21,761 capitalized bigram continuations that surfaced through suggestions and next-word predictions regardless of shift state.

### Changed
- Renumbered the digit keys into a phone-number-pad shape: `AB`=1, `CD`=2, `EF`=3, `IJ`=4, `KL`=5, `MN`=6, `QR`=7, `ST`=8, `UV`=9, `SPACE`=0 - columns 2-4 of rows 1-3 now read as a 3x3 pad with 0 centered below on the space bar, matching a real dial pad. `.,!?`, `GH`, and `OP` sit outside the pad and now show `@`, `*`, `#` respectively instead of digits.
- Enter's corner hint is now a plain accent-colored dot instead of an emoji, matching the app's other small circular status indicators (long-press still opens the emoji picker).
- Backspace's icon and active-SHIFT's label now pick white or near-black content color based on the chosen accent's WCAG relative luminance, instead of hard-coded white - fixes unreadable content against light accents (e.g. yellow, teal).
- Toolbar Settings/Edit icons are now always the muted hint color instead of always accent-tinted; only the `xt9` toggle (an actual toggle, not a one-shot action) reflects the accent color.
- `EmojiPickerView`'s top bar and emoji grid now use the shared `keyboard_background` color instead of hard-coded `#111111`/`#2B2B2B`, so the emoji panel matches the rest of the keyboard's palette.
- `EmojiSearchKey` (the emoji-search QWERTY) and the Symbols page's symbol cells now use the same 16dp radius / `key_surface` fill / hairline border as the main grid, instead of a flatter, differently-rounded style.

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
