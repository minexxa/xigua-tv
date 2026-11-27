plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.xigua.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xigua.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"

        // 精简：TV/盒子只保留 ARM 架构，剔除 x86/x86_64
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // 签名可选：仅当环境变量提供了 keystore 文件才启用。
    // CI 里从 secrets 解码 keystore 后设置这些环境变量；本地直接 assembleRelease 则产出无签名包。
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("SIGNING_KEYSTORE_FILE")
            val ksFile = if (!ksPath.isNullOrBlank()) file(ksPath) else null
            if (ksFile != null && ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 精简核心：R8 代码压缩 + 资源收缩
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 有 keystore 才签名，否则产出 unsigned release
            val ksPath = System.getenv("SIGNING_KEYSTORE_FILE")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // 精简：release 不打包 debug 专用信息
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose (BOM 统一版本)
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // 精简：tooling-preview 仅调试需要，移入 debug 变体
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3 ExoPlayer —— 实测 Feed 直链均为 MP4，不引入 HLS/DASH 以减小体积
    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // 网络: OkHttp + Kotlinx Serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 图片加载
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 生命周期
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Room 观看历史 / 进度记忆
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore 播放设置持久化（1.0.0 为纯 JVM artifact，1.1.x 迁移 KMP 后 Android API 需 -android 变体）
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
