import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// FBR Clients for Windows (Compose for Desktop). Same logic as the phone app (shared/).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    // Small Compose parts shared with the phone app.
    sourceSets.getByName("main").kotlin.srcDir("../shared-ui/src/main/kotlin")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.orgjson)
    // Windows data protection (DPAPI) for the keys, through JNA.
    implementation(libs.jna.platform)
    // In-app browser: Chromium (CEF) for Java. The Windows engine (~160 MB) goes into
    // the installer only when building on Windows; it is unpacked on first use.
    implementation(libs.jcefmaven)
    if (System.getProperty("os.name").lowercase().startsWith("windows")) runtimeOnly(libs.jcef.natives.windows)

    testImplementation(libs.junit)
}

// Windows needs a version like 1.0.<build> (each part at most 255.255.65535).
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt().coerceIn(1, 65535)
val releaseVersion = rootProject.file("version.txt").readText().trim()
val msiVersion = releaseVersion.split(".").let { p -> "${p[0]}.${p.getOrElse(1) { "0" }}.$buildNumber" }

compose.desktop {
    application {
        mainClass = "pk.fbrclients.desktop.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            // Needed by the Chromium browser on newer Java.
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "FBR Clients"
            packageVersion = msiVersion
            description = "FBR Clients for Windows"
            vendor = "FBR Clients"
            // Modules the app needs from Java (crypto, XML for Excel files, desktop).
            modules("java.xml", "java.naming", "jdk.unsupported", "jdk.crypto.ec", "java.management", "java.logging", "java.sql")
            windows {
                menuGroup = "FBR Clients"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Never change: lets a new installer replace the old version.
                upgradeUuid = "6f0b8d2e-5c1a-4f7e-9b0a-3d2c1e4f5a6b"
                iconFile.set(project.file("icons/app.ico"))
            }
        }
    }
}

tasks.test {
    // ScreenshotTest draws the screens into this folder when it is set (review builds).
    inputs.property("screenshots", System.getenv("SCREENSHOTS_DIR") ?: "")
}
