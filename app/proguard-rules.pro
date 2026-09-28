# LSPosed 现代 API 模块混淆规则
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 保留入口类（若未启用混淆，以下为兜底）
-keep class com.carwith.enhance.mg4.CarLinkModEntry { *; }
-keep class com.carwith.enhance.mg4.HookInstaller { *; }

# 保留 Application / Activity（Manifest 引用的组件）
-keep class com.carwith.enhance.mg4.CarModApplication { *; }
-keep class com.carwith.enhance.mg4.MainActivity { *; }
# 桌面图标 alias（被字符串 ComponentName 引用，混淆会导致隐藏图标功能失效）
-keep class com.carwith.enhance.mg4.MainActivityLauncher { *; }

# libxposed service 运行时反射
-keep class io.github.libxposed.service.** { *; }
-dontwarn io.github.libxposed.service.**

# miuix / compose 相关
-dontwarn org.jetbrains.compose.**
-dontwarn androidx.compose.**
-dontwarn top.yukonga.miuix.**

# ===== 发布脱敏（2026-09-28 追加）=====
# 抹除源码文件名：反编译后 SourceFile 显示空/星号，无法反推源码结构
-renamesourcefileattribute ""
# 保留行号（崩溃栈可定位），与上面的文件名抹除不冲突
-keepattributes SourceFile,LineNumberTable