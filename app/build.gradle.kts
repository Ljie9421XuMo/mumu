import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 本地密钥优先从 local.properties / 环境变量读（CI 里没有 local.properties）。
// 这两个值都是可公开的客户端配置，publishable key 本来就是要放进客户端的。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(key: String, fallback: String): String =
    localProps.getProperty(key) ?: System.getenv(key) ?: fallback

val supabaseUrl = cfg("SUPABASE_URL", "https://qhbdbkttnmevgsovrset.supabase.co")
val supabaseKey = cfg("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_LTnytTrSuIK8_WLOLkyv6g_f-WO4LFn")

android {
    namespace = "com.mumu.pet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mumu.pet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.3.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
}
