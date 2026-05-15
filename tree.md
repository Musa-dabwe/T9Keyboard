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
│       │   │   ├── en_us_words.txt
│       │   │   ├── main_en_US.combined.txt
│       │   │   └── ubuntu.ttf
│       │   ├── font/
│       │   │   ├── ubuntu_sans_mono.ttf
│       │   │   └── ubuntu_sans_mono_bold.ttf
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
│       │       ├── layout/
│       │       ├── menu/
│       │       ├── values/
│       │       └── xml/
│       └── test/
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── tools/
│   └── convert_dict.py
├── BUILD.md
├── CHANGELOG.md
├── DEPENDENCY_REPORT.md
├── OPTIMIZATION.md
├── README.md
├── REFACTOR_LOG.md
├── SECURITY.md
├── SECURITY_REPORT.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── keystore.properties.template
└── settings.gradle.kts
```
