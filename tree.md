# Project Structure

```
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── en_us_bigrams.txt
│       │   │   └── en_us_words.txt
│       │   ├── kotlin/com/musa/t9keyboard/
│       │   │   ├── utils/
│       │   │   │   ├── FontUtils.kt
│       │   │   │   └── ImeUtils.kt
│       │   │   ├── ActionListeners.kt
│       │   │   ├── AospBigrams.kt
│       │   │   ├── AospDictionary.kt
│       │   │   ├── AppBlacklistActivity.kt
│       │   │   ├── ContactsDictionary.kt
│       │   │   ├── CrashLogger.kt
│       │   │   ├── DebugLogsActivity.kt
│       │   │   ├── EditKeyHandler.kt
│       │   │   ├── EditorState.kt
│       │   │   ├── EmojiAdapter.kt
│       │   │   ├── EmojiData.kt
│       │   │   ├── EmojiPickerView.kt
│       │   │   ├── EmojiSearchData.kt
│       │   │   ├── EmojiSearchEngine.kt
│       │   │   ├── EmojiSearchPanelView.kt
│       │   │   ├── InputConnectionManager.kt
│       │   │   ├── KeyLabelRenderer.kt
│       │   │   ├── KeyTouchHandler.kt
│       │   │   ├── KeyboardView.kt
│       │   │   ├── LearnedDictionary.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── PasteClipboardManager.kt
│       │   │   ├── PreferencesManager.kt
│       │   │   ├── SettingsActivity.kt
│       │   │   ├── SettingsDialogHelper.kt
│       │   │   ├── SetupActivity.kt
│       │   │   ├── ShiftStateManager.kt
│       │   │   ├── SuggestionBar.kt
│       │   │   ├── SuggestionEngine.kt
│       │   │   ├── SwipeDownListener.kt
│       │   │   ├── SymbolsView.kt
│       │   │   ├── T9InputMethodService.kt
│       │   │   ├── T9Utils.kt
│       │   │   ├── TextEditingView.kt
│       │   │   └── ViewOrchestrator.kt
│       │   └── res/
│       │       ├── drawable/
│       │       ├── font/
│       │       ├── layout/
│       │       ├── menu/
│       │       ├── values/
│       │       └── xml/
│       └── test/
├── docs/
│   └── audits/
│       ├── DEAD_CODE_REPORT.md
│       ├── DEPENDENCY_REPORT.md
│       ├── OPTIMIZATION.md
│       ├── README.md
│       ├── REFACTOR_LOG.md
│       └── SECURITY_REPORT.md
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── tools/
│   ├── raw/
│   │   └── main_en_US.combined.txt
│   └── convert_dict.py
├── BUILD.md
├── CHANGELOG.md
├── README.md
├── SECURITY.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── keystore.properties.template
└── settings.gradle.kts
```
