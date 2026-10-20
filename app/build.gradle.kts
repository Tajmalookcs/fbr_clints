plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI passes the run number so every build can be installed over the previous one.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()

// Release signing comes from GitHub Secrets (see .github/workflows/build.yml).
// Nothing secret is ever stored in this repo.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "pk.fbrclients.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "pk.fbrclients.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        // Release builds (tag v1.0.0) pass VERSION_NAME=1.0.0; others are 0.1.<run>.
        versionName = System.getenv("VERSION_NAME") ?: "0.1.$buildNumber"

    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // The shared code (formats, maths, import, backup, sync) is compiled into the app
    // directly, so Room sees the entity classes as source. Its tests run in :shared.
    sourceSets.getByName("main").java.srcDir("../shared/src/main/kotlin")
    // Small Compose parts (amount field, dropdown, dialogs) also used by the PC app.
    sourceSets.getByName("main").java.srcDir("../shared-ui/src/main/kotlin")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Saved schemas let later versions migrate the database without losing data.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (Android's copy is only a stub there).
    testImplementation(libs.orgjson)
    // Runs the database upgrade SQL on a real SQLite in unit tests.
    testImplementation(libs.sqlite.jdbc)
}
