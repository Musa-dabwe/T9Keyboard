/private fun onPasteBubbleTapped() {/,/}/ {
    s/pasteBubble?.visibility = View.GONE/pasteBubble?.visibility = View.GONE\n        clipboardUsed = true/
}
/private fun updatePasteBubble(info: EditorInfo?) {/,/}/ {
    s/if (isCurrentFieldSensitive(info) || preferences.isAppBlacklisted(currentPackageName)) {/if (isCurrentFieldSensitive(info) || preferences.isAppBlacklisted(currentPackageName) || clipboardUsed) {/
}
