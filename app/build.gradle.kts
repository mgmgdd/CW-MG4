plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// ===== 签名配置：密码一律走环境变量（本地 shell 导出 / GitHub Secrets），绝不落盘明文 =====
val storeFilePath: String? = System.getenv("KEYSTORE_PATH")
val storePass: String? = System.getenv("KEYSTORE_PASSWORD")
val keyPass: String? = System.getenv("KEY_ALIAS_PASSWORD")
val keyAliasName: String? = System.getenv("KEY_ALIAS")

android {
    namespace = "com.carwith.enhance.mg4"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carwith.enhance.mg4"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.6.0"

        ndk {
            // 只保留真机 ABI，排除 x86/x86_64 模拟器库
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            // 云端（GitHub Actions）：keystore 由 Secrets 解码到本地文件，KEYSTORE_PATH 指向它
            // 本地（proot）：KEYSTORE_PATH 指向手机上的正式 keystore
            storeFile = storeFilePath?.let { file(it) }
            storePassword = storePass ?: ""
            // 显式指定格式：正式 keystore 是 PKCS12，避免因 .jks 文件名误导 AGP 按 JKS 解析报错
            storeType = "PKCS12"
            keyAlias = keyAliasName ?: "cw-mg4"
            keyPassword = keyPass ?: ""
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源压缩，减小 APK 体积（入口/hook 类已在 proguard-rules.pro 中 keep）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 仅在 ARM64（proot 本地）环境强制使用 AAPT2 linux-aarch64；
// x86_64（GitHub Actions / 常规 CI）走官方分发，不受影响。
if (System.getProperty("os.arch") == "aarch64") {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
            }
        }
    }
}

dependencies {
    // LSPosed 现代 API（Hook 侧），compileOnly，运行时由框架提供
    compileOnly(libs.libxposed.api)
    // 模块 App 侧与框架通信（RemotePreferences 写入配置）
    implementation(libs.libxposed.service)
    compileOnly(libs.androidx.annotation)

    // activity-compose（ComponentActivity / setContent）
    implementation(libs.androidx.activity.compose)

    // navigationevent-compose（miuix Overlay 弹窗依赖 LocalNavigationEventDispatcherOwner）
    implementation(libs.androidx.navigationevent.compose)

    // miuix UI（Compose Multiplatform 实现）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    // miuix 图标库（返回箭头 Back 等）
    implementation(libs.miuix.icons)
    // Compose 动画（页面切换过渡）
    implementation(compose.animation)
}