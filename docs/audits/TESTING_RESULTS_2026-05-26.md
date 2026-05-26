# Manual Testing Results - May 26, 2026

## Testing Overview

**Test Date**: 2026-05-26
**Tester**: User Testing + Claude Code Analysis
**Test Environment**: Production Build
**Status**: 7/12 Complete, 2 Critical Issues Identified

---

## Test Results Summary

### ✅ Completed Tests (7)

| Test | Status | Notes |
|------|--------|-------|
| Profile memory with Android Studio Profiler | ✅ COMPLETE | Memory profiling completed successfully |
| Test multi-tap timing edge cases | ✅ COMPLETE | Multi-tap timing works well, no edge cases found |
| Verify sensitive input suppression | ✅ COMPLETE | Password field detection working correctly |
| Test emoji picker scrolling performance | ✅ COMPLETE | Scrolling performance is excellent |
| Verify contact suggestions | ✅ COMPLETE | Working well with permission granted/denied |
| Test theme switching | ✅ COMPLETE | Theme switching works flawlessly |
| Test all accent colors | ✅ COMPLETE | All accent colors rendering correctly |
| Verify haptic and audio feedback | ✅ COMPLETE | Haptic and audio feedback working perfectly |

### ❌ Incomplete Tests (3)

| Test | Status | Reason |
|------|--------|--------|
| Test on 1GB RAM device | ❌ NOT COMPLETE | Requires physical device testing |
| Test app blacklist functionality | ❌ NOT COMPLETE | Requires further testing and validation |
| Test XT9 prediction accuracy | 🔴 CRITICAL ISSUES | Multiple issues identified - see below |

### 🔧 Tests Requiring Changes (1)

| Test | Status | Required Action |
|------|--------|-----------------|
| Verify dictionary expiration | 🔧 NEEDS REFINEMENT | Add user-configurable settings |

---

## Critical Issues Identified

### 🔴 ISSUE #1: XT9 Frequency Conflict (CRITICAL)

**Priority**: HIGH
**Category**: Prediction Accuracy
**Status**: Open

#### Description
Learned words are conflicting with base dictionary frequency words, causing incorrect prediction ordering.

#### Reproduction
1. Enable XT9 mode
2. Tap key 4 (GHI)
3. **Expected**: Letter 'I' should appear (as it's both a letter and a common word)
4. **Actual**: Letter 'g' appears instead

#### Root Cause Analysis
- Frequency calculation between learned dictionary and base dictionary is not properly weighted
- Single-letter words (especially "I") not given priority over multi-letter words with same digit sequence
- Learned word frequencies may be incorrectly boosting lowercase alternatives

#### Impact
- Reduced typing efficiency for users
- Common single-letter words buried in suggestions
- Breaks user expectations for predictive text

#### Proposed Solution
1. **Priority System Implementation**:
   ```kotlin
   // Suggested priority order
   enum class WordPriority {
       SINGLE_LETTER_WORD,      // Highest (e.g., "I", "a")
       CONTACT_NAME,            // High
       BASE_DICTIONARY_HIGH,    // High (freq > 1000)
       LEARNED_WORD_FREQUENT,   // Medium (typed > 10 times)
       BASE_DICTIONARY_MEDIUM,  // Medium
       LEARNED_WORD_RARE,       // Low
       BASE_DICTIONARY_LOW      // Low
   }
   ```

2. **Frequency Conflict Resolution**:
   - Implement weighted scoring system
   - Single-letter words get automatic +1000 boost
   - Contact names get +500 boost
   - Learned words: base_freq + (typed_count * 10)

3. **Files to Modify**:
   - `SuggestionEngine.kt`: Add priority-based sorting
   - `LearnedDictionary.kt`: Adjust frequency calculation
   - `AospDictionary.kt`: Add single-letter word detection

---

### 🔴 ISSUE #2: Inconsistent Capitalization Rules (CRITICAL)

**Priority**: HIGH
**Category**: Text Processing
**Status**: Open

#### Description
Words are being auto-capitalized inconsistently, violating standard English capitalization rules.

#### Current Behavior
- Common words like "As", "Love" are automatically capitalized
- No clear rule system for determining when to capitalize

#### Required Capitalization Rules

**✅ SHOULD Capitalize**:
1. **Proper Nouns**:
   - User-defined names
   - Contact names from phone
   - Language names (e.g., "English", "French")
   - Country names (e.g., "America", "Canada")
   - Deity names (e.g., "God", "Allah", "Buddha")

2. **Special Cases**:
   - The letter "I" when used as a pronoun
   - First letter of sentence (after period, question mark, exclamation)
   - User manually shifts (one-shot or caps lock mode)

**❌ SHOULD NOT Capitalize**:
- Common words (e.g., "as", "love", "the", "and")
- Verbs (e.g., "go", "run", "think")
- Adjectives (e.g., "happy", "sad", "big")
- Any word that isn't a proper noun or special case

#### Impact
- Incorrect grammar in user text
- Requires manual correction by user
- Breaks professional communication expectations

#### Proposed Solution

1. **Create Capitalization Rule Engine**:
   ```kotlin
   object CapitalizationRules {
       // Reserved proper nouns (languages, countries, deities)
       private val properNouns: Set<String> = setOf(
           // Languages
           "english", "spanish", "french", "german", "chinese", /* ... */
           // Countries
           "america", "canada", "mexico", "england", /* ... */
           // Deities
           "god", "allah", "buddha", "jesus", "krishna", /* ... */
       )

       fun shouldCapitalize(
           word: String,
           isContactName: Boolean,
           isStartOfSentence: Boolean,
           isSingleLetterI: Boolean
       ): Boolean {
           return when {
               isSingleLetterI && word == "i" -> true
               isStartOfSentence -> true
               isContactName -> true
               word.lowercase() in properNouns -> true
               else -> false
           }
       }
   }
   ```

2. **Integration Points**:
   - `T9InputMethodService.kt`: Call rule engine before committing text
   - `SuggestionEngine.kt`: Apply capitalization rules to suggestions
   - `LearnedDictionary.kt`: Store lowercase versions only, capitalize on display

3. **Files to Modify**:
   - Create new file: `CapitalizationRules.kt`
   - Modify: `T9InputMethodService.kt`
   - Modify: `SuggestionEngine.kt`
   - Modify: `LearnedDictionary.kt`

---

## Feature Enhancement Required

### 🔧 ENHANCEMENT #1: User-Configurable Dictionary Expiration

**Priority**: MEDIUM
**Category**: Privacy Settings
**Status**: Open

#### Current Implementation
- Hard-coded 180-day expiration constant
- No user control over learned word retention period

#### Requested Feature
Add "Dictionary Word Expiration" setting to Settings screen with the following options:

| Option | Duration | Use Case |
|--------|----------|----------|
| 24 hours | 86,400,000 ms | Maximum privacy, temporary device |
| 7 days | 604,800,000 ms | Short-term usage |
| 14 days | 1,209,600,000 ms | Medium-term usage |
| **31 days** (default) | **2,678,400,000 ms** | **Balanced approach** |

#### Implementation Plan

1. **Add PreferencesManager Property**:
   ```kotlin
   companion object {
       const val KEY_EXPIRATION_DAYS = "expiration_days"
       const val DEFAULT_EXPIRATION_DAYS = 31
   }

   var expirationDays: Int
       get() = prefs.getInt(KEY_EXPIRATION_DAYS, DEFAULT_EXPIRATION_DAYS)
       set(value) = prefs.edit().putInt(KEY_EXPIRATION_DAYS, value).apply()

   val expirationMs: Long
       get() = expirationDays * 86_400_000L
   ```

2. **Update LearnedDictionary**:
   ```kotlin
   object LearnedDictionary {
       // Remove hard-coded constant
       // private const val EXPIRATION_MS = 180L * 86_400_000L

       fun isExpired(word: String, now: Long, expirationMs: Long): Boolean {
           val timestamp = lastTypedMap[word] ?: return true
           return (now - timestamp) > expirationMs
       }

       fun load(context: Context, preferences: PreferencesManager) {
           val expirationMs = preferences.expirationMs
           // Use dynamic expiration...
       }
   }
   ```

3. **Add Settings UI**:
   - File: `app/src/main/res/xml/preferences.xml`
   - Add ListPreference with 4 options
   - Default: "31 days"

4. **Files to Modify**:
   - `PreferencesManager.kt`: Add new property
   - `LearnedDictionary.kt`: Replace constant with parameter
   - `T9InputMethodService.kt`: Pass preferences to load()
   - `app/src/main/res/xml/preferences.xml`: Add UI element
   - `app/src/main/res/values/strings.xml`: Add labels

---

## Incomplete Testing

### ⏳ Pending: 1GB RAM Device Testing

**Status**: NOT COMPLETE
**Reason**: Requires physical device access

**Testing Requirements**:
1. Install APK on Android device with ≤1GB RAM
2. Test with API 25 (Android 8.0) or higher
3. Monitor memory usage during:
   - Initial launch
   - Dictionary loading
   - Active typing (50+ words)
   - Switching between keyboard modes
   - Emoji picker usage
4. Measure:
   - Startup time (target: <500ms)
   - PSS memory footprint (target: <50MB)
   - Suggestion generation latency (target: <16ms)

**Recommended Devices**:
- Samsung Galaxy J2 (1GB RAM, API 25-28)
- Nokia 2.1 (1GB RAM, API 27-29)
- Moto E5 Play (1GB RAM, API 27-28)

---

### ⏳ Pending: App Blacklist Functionality

**Status**: NOT COMPLETE
**Reason**: Requires further testing

**Testing Requirements**:
1. Add app to blacklist via Settings
2. Switch to blacklisted app
3. Verify word learning is suppressed
4. Type 10+ words
5. Check LearnedDictionary for absence of words
6. Remove app from blacklist
7. Verify word learning resumes

**Known Issue**:
- App Blacklist requires `QUERY_ALL_PACKAGES` permission on Android 11+
- Current implementation may not work on API 30+

---

## Next Steps

### Immediate Actions

1. **🔴 HIGH PRIORITY - Fix XT9 Frequency Conflict**:
   - Investigate SuggestionEngine scoring logic
   - Implement priority-based word ranking
   - Test single-letter word prioritization
   - Estimated Time: 4-6 hours

2. **🔴 HIGH PRIORITY - Implement Capitalization Rules**:
   - Create CapitalizationRules.kt
   - Build proper noun dictionary
   - Integrate with suggestion engine
   - Test across various scenarios
   - Estimated Time: 6-8 hours

3. **🔧 MEDIUM PRIORITY - Add Expiration Settings**:
   - Add PreferencesManager properties
   - Update LearnedDictionary API
   - Create Settings UI
   - Test migration from hard-coded value
   - Estimated Time: 3-4 hours

### Validation Testing

After fixes are implemented:
1. Re-test XT9 prediction accuracy
2. Validate capitalization across word types
3. Test dictionary expiration with all timeframe options
4. Profile memory usage again
5. Complete 1GB device testing
6. Validate app blacklist functionality

---

## Documentation Updates

- ✅ CLAUDE.md updated with test results
- ✅ Known Technical Debt section updated
- ✅ Recommendations section prioritized
- ✅ Manual Testing Checklist marked with status
- ✅ This document created for tracking

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-26 | Claude Code | Initial test results documentation |

---

**Document Version**: 1.0
**Last Updated**: 2026-05-26
