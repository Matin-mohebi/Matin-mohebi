import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: only a complete keystore.properties configuration may
// sign a release. Without it, the release variant remains unsigned; debug builds
// remain available for local testing.
// keystore.properties and *.jks are git-ignored — never commit secrets.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "ir.matincode.matinai"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.matincode.matinai"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "1.6.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    val releaseSigningConfigured = keystorePropsFile.exists() &&
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
            !keystoreProps.getProperty(it).isNullOrBlank()
        }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("release") else null
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
