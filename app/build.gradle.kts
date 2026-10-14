import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val localGeminiApiKey = providers.environmentVariable("GEMINI_API_KEY").orNull
    ?: localProperties.getProperty("GEMINI_API_KEY", "")
val geminiModel = providers.environmentVariable("GEMINI_MODEL").orNull
    ?: localProperties.getProperty("GEMINI_MODEL", "gemini-3.5-flash-lite")
val aiProxyBaseUrl = providers.environmentVariable("AI_PROXY_BASE_URL").orNull
    ?: localProperties.getProperty("AI_PROXY_BASE_URL", "")
val audioDbApiKey = providers.environmentVariable("THEAUDIODB_API_KEY").orNull
    ?: localProperties.getProperty("THEAUDIODB_API_KEY", "123")

fun asBuildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.finalproject1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.finalproject1"
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", asBuildConfigString(""))
        buildConfigField("String", "GEMINI_MODEL", asBuildConfigString(geminiModel))
        buildConfigField("String", "AI_PROXY_BASE_URL", asBuildConfigString(aiProxyBaseUrl))
        buildConfigField("String", "THEAUDIODB_API_KEY", asBuildConfigString(audioDbApiKey))
    }

    buildTypes {
        debug {
            buildConfigField("String", "GEMINI_API_KEY", asBuildConfigString(localGeminiApiKey))
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.material)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
