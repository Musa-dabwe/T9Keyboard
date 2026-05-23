# Suggestion Prefix Cache

## 1. Overview
The suggestion prefix cache is an in-memory cache-aside layer sitting in front of the existing BKTree/LearnedDictionary lookup pipeline. Its purpose is to return suggestions instantly for recently typed prefixes without re-querying the dictionary on every keypress. This significantly improves UI responsiveness, especially on lower-end devices where dictionary lookups might incur noticeable latency.

## 2. Where It Fits in the Codebase
- The cache lives in the suggestion delegate class: `SuggestionEngine.kt`.
- It intercepts calls **before** the complex lookup logic (BKTree and `LearnedDictionary`) is queried.
- On a cache miss, the result from the dictionary lookups is stored in the cache before being returned to the UI.

## 3. Data Structure
The cache uses a `LinkedHashMap` configured for LRU (Least Recently Used) eviction.

```kotlin
private val suggestionCache = object : LinkedHashMap<String, List<String>>(
    MAX_CACHE_SIZE, 0.75f, true
) {
    override fun removeEldestEntry(eldest: Map.Entry<String, List<String>>) =
        size > MAX_CACHE_SIZE
}
private const val MAX_CACHE_SIZE = 100
```

### Why LinkedHashMap?
By setting `accessOrder = true` (the third constructor parameter), the `LinkedHashMap` reorders entries based on access rather than insertion. Combined with `removeEldestEntry`, this provides a robust LRU cache with minimal boilerplate.

## 4. Cache-Aside Logic (Pseudocode + Kotlin)
The lookup flow follows a standard cache-aside pattern:
1. Check if the prefix key exists in `suggestionCache`.
2. **If hit:** Return the cached list of suggestions immediately.
3. **If miss:**
   - Perform the full query against `LearnedDictionary`, `BKTree`, and other sources.
   - Store the resulting list in `suggestionCache` using the prefix as the key.
   - Return the result.

### Kotlin Implementation Snippet
```kotlin
fun getSuggestions(prefix: String): List<String> {
    // 1. Check cache
    suggestionCache[prefix]?.let { return it }

    // 2. Cache miss - run expensive lookup
    val results = performFullDictionaryLookup(prefix)

    // 3. Store and return
    suggestionCache[prefix] = results
    return results
}
```

## 5. Cache Invalidation Rules
To ensure suggestion accuracy, the cache **must** be cleared in the following scenarios:
- **New Input Session:** When the user's input is fully cleared or reset (e.g., after committing a word).
- **Dictionary Update:** When a new word is added to `LearnedDictionary`. Since learned words influence ranking, cached results for that prefix are now stale.
- **Sensitive Input:** When the keyboard switches to a sensitive field (password, numeric, etc.) detected via `SafeBox` or `EditorInfo`.

## 6. Thread Safety Note
In the current architecture, suggestion fetching runs within a dedicated coroutine scope. The cache is accessed exclusively from that coroutine (not directly from the main thread).
- **No additional synchronization** is required as long as access remains confined to this single-threaded coroutine context.
- **Warning:** If the architecture changes to allow multi-threaded access, the cache must be wrapped using `Collections.synchronizedMap()` or replaced with a thread-safe alternative.

## 7. What NOT to Do
- **No Persistence:** Do not persist the cache to disk. It is intended for session-only memory.
- **Minimum Prefix Length:** Do not cache results for empty or single-digit prefixes. These are too broad and fluctuate too rapidly to be worth caching.
- **No Write-Behind:** Avoid using write-behind for `LearnedDictionary` updates. Always use write-through to prevent data loss in the event of a crash.

## 8. Implementation Checklist
- [ ] Add `suggestionCache` and `MAX_CACHE_SIZE` to the suggestion delegate
- [ ] Wrap `getSuggestions()` with cache-aside logic
- [ ] Call `suggestionCache.clear()` on input reset
- [ ] Call `suggestionCache.clear()` after `LearnedDictionary` write
- [ ] Add a log line (debug builds only) indicating cache hit vs miss
- [ ] Verify no suggestions regression by typing 10+ word sentence and checking Logcat
