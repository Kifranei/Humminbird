# ProGuard rules for Halcyon

# JNI entry points are resolved by class and method names.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Lyricon is a provider-facing API; keep its published model names stable.
-keep class io.github.proify.lyricon.** { *; }
-dontwarn io.github.proify.lyricon.**

# SuperLyricApi references this hidden framework class on supported systems.
-keep class com.hchen.superlyricapi.** { *; }
-dontwarn android.os.ServiceManager

# Shizuku starts this class reflectively in a separate user-service process and binds to the AIDL
# interface by descriptor. Keep both the constructor and generated Binder methods in release builds.
-keep class com.ella.music.shizuku.ShizukuShellService { <init>(android.content.Context); *; }
-keep interface com.ella.music.shizuku.IShizukuShellService { *; }

# Lyric Getter's Xposed module finds and hooks the public API by class and member names.
-keep class cn.lyric.getter.api.** { *; }

# Settings search enumerates setting resources to keep new preferences discoverable. R8 must retain
# these reflected fields in minified builds.
-keepclassmembers class com.ella.music.R$string {
    public static int settings_*;
}


# FFmpeg native symbols use Java_androidx_media3_decoder_ffmpeg_* names.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# Ktor / MCP SDK — suppress warnings for JVM-only classes not available on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn io.ktor.**
-dontwarn io.modelcontextprotocol.**

# SMB uses NTLM/anonymous authentication. JVM Kerberos and optional expression filters
# are not used on Android; their APIs are absent from the framework.
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
# SMBJ's event bus discovers annotated handlers reflectively.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep @interface net.engio.mbassy.listener.Handler { *; }
-keepclassmembers class com.hierynomus.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
# MBassador's SubscriptionFactory obtains this public constructor through
# Handler.invocation().getConstructor(SubscriptionContext.class). Retaining the
# annotation and handler methods alone does not preserve that constructor in R8.
-keep,allowobfuscation class net.engio.mbassy.dispatch.ReflectiveHandlerInvocation {
    public <init>(net.engio.mbassy.subscription.SubscriptionContext);
}

# OpenCC obtains these defaults through Heaven's Class.newInstance() factory.
# Losing a constructor makes ZhConverterUtil initialization fail in Release.
-keep,allowobfuscation class com.github.houbb.opencc4j.support.datamap.impl.DataMapDefault { public <init>(); }
-keep,allowobfuscation class com.github.houbb.opencc4j.support.match.impl.SimpleZhMatchAll { public <init>(); }
-keep,allowobfuscation class com.github.houbb.opencc4j.support.segment.impl.FastForwardSegment { public <init>(); }
# FastForwardSegment initializes the NLP default using the same factory.
-keep,allowobfuscation class com.github.houbb.nlp.common.dfa.tree.impl.DefaultTrieTreeMap { public <init>(); }

# SSH algorithms and the enabled authentication methods are selected by class name.
# Desktop agent/socket/logging integrations are not part of password-based TCP SFTP.
# Session reflectively instantiates package-private authentication and key-exchange
# classes; preserve its package and call sites, too.
-keep class com.jcraft.jsch.Session { *; }
-keep class com.jcraft.jsch.jce.** { *; }
-keep class com.jcraft.jsch.bc.** { *; }
-keep class com.jcraft.jsch.DH* { *; }
-keep class com.jcraft.jsch.UserAuthNone { *; }
-keep class com.jcraft.jsch.UserAuthPassword { *; }
-keep class com.jcraft.jsch.UserAuthKeyboardInteractive { *; }
# Legacy FTP LIST parsers are instantiated by their class names.
-keep class org.apache.commons.net.ftp.parser.** { public <init>(...); }
# Dictionary suppliers are also constructed by Class.newInstance().
-keep,allowobfuscation class com.github.houbb.opencc4j.support.data.impl.** { public <init>(); }
