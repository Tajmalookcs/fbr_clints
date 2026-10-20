import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain JVM code shared by the phone app (app/) and the PC app (desktop/).
// No Android classes may be used here.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Room annotations only (the phone database reads them; the PC app ignores them).
    compileOnly(libs.androidx.room.common)
    // Android has org.json built in; the PC app adds the library itself.
    compileOnly(libs.orgjson)

    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
    testImplementation(libs.androidx.room.common)
}
