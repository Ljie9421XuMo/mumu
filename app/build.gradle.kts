import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// \u672c\u5730\u5bc6\u94a5\u4f18\u5148\u4ece local.properties / \u73af\u5883\u53d8\u91cf\u8bfb\uff08CI \u91cc\u6ca1\u6709 local.properties\uff09\u3002
// \u8fd9\u4e24\u4e2a\u503c\u90fd\u662f\u53ef\u516c\u5f00\u7684\u5ba2\u6237\u7aef\u914d\u7f6e\uff0cpublishable key \u672c\u6765\u5c31\u662f\u8981\u653e\u8fdb\u5ba2\u6237\u7aef\u7684\u3002
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
        versionCode = 2
        versionName = "0.3.1"

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
