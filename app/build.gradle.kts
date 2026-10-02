plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.example.annotator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.annotator"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    signingConfigs {
        create("release") {
            // Nothing here is stored anywhere: every value is read from the terminal at build
            // time (falling back to env vars if set), so no secret ever sits in a file, env var,
            // or shell history. Uses plain stdin (not System.console(), which Gradle's forked
            // build process does not reliably provide) and only prompts for a release task, so
            // plain debug builds are unaffected.
            // Note: input is NOT masked (no console == no hidden input), so passwords are
            // visible on screen as you type them here.
            val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release") }
            if (isReleaseBuild) {
                fun prompt(label: String): String? {
                    print("$label: ")
                    System.out.flush()
                    return readLine()
                }
                val keystorePath = System.getenv("VERITYSEE_KEYSTORE") ?: prompt("Keystore path")
                val alias = System.getenv("VERITYSEE_KEY_ALIAS") ?: prompt("Key alias")
                val storePass = System.getenv("VERITYSEE_KEYSTORE_PASSWORD") ?: prompt("Keystore password")
                val keyPass = System.getenv("VERITYSEE_KEY_PASSWORD") ?: prompt("Key password")
                if (!keystorePath.isNullOrBlank()) storeFile = file(keystorePath)
                keyAlias = alias
                storePassword = storePass
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
