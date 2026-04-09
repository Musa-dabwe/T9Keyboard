# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# T9 Keyboard ProGuard Rules

# IME Entry Points
-keep class com.musa.t9keyboard.T9InputMethodService { *; }
-keepclassmembers class * extends android.inputmethodservice.InputMethodService { *; }

# Dictionaries
-keep class com.musa.t9keyboard.AospDictionary { *; }
-keep class com.musa.t9keyboard.AospBigrams { *; }
-keep class com.musa.t9keyboard.LearnedDictionary { *; }
-keep class com.musa.t9keyboard.ContactsDictionary { *; }

# Emoji Data
-keep class com.musa.t9keyboard.EmojiData { *; }
-keep class com.musa.t9keyboard.EmojiSearchData { *; }

# EmojiCompat
-keep class androidx.emoji2.text.** { *; }
-keep class androidx.emoji2.widget.** { *; }

# FontUtils asset loading
-keep class com.musa.t9keyboard.utils.FontUtils { *; }
