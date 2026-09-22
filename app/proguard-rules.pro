# Preserve JavascriptInterface methods on DioxaminePluginBridge from R8 obfuscation/stripping
-keepclassmembers class io.github.rhythmcache.dioxamine.plugin.DioxaminePluginBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Bouncy Castle Provider reflection rules (required for adb-kt key generation & TLS)
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn org.bouncycastle.**

# Termux terminal emulator and view
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }

