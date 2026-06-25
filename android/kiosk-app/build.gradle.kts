import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

android {
    namespace = "com.esper.foodsafety"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.esper.foodsafety"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anon.key", "")}\"")
        buildConfigField("String", "AI_API_BASE", "\"${localProps.getProperty("ai.api.base", "http://localhost:8000")}\"")
        buildConfigField("String", "SITE_ID",     "\"${localProps.getProperty("site.id",     "")}\"")
        buildConfigField("String", "SITE_NAME",   "\"${localProps.getProperty("site.name",   "")}\"")
        buildConfigField("String", "DEVICE_ID",   "\"${localProps.getProperty("device.id",   "")}\"")
        buildConfigField("String", "DEVICE_NAME", "\"${localProps.getProperty("device.name", "")}\"")
        // On-device model: defaults to TinyLlama (Apache-2.0, no login needed, ~670 MB).
        // For better quality set on.device.model.url + on.device.model.filename in local.properties
        // to point at Gemma 3 1B QAT Q4_0 (needs HF token) or Qwen2.5-1.5B Q4_K_M (~1 GB).
        buildConfigField("String", "ON_DEVICE_MODEL_URL",
            "\"${localProps.getProperty("on.device.model.url",
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf")}\"")
        buildConfigField("String", "ON_DEVICE_MODEL_FILENAME",
            "\"${localProps.getProperty("on.device.model.filename", "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf")}\"")

        ndk {
            // Kiosk tablet is ARM64; emulator-5554 is also arm64-v8a.
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
      prefab = false
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

  implementation("androidx.browser:browser:1.8.0")

  // On-device LLM — compiled from llama.cpp sources via NDK (see src/main/cpp)
}
