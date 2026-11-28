import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// ── Secrets ────────────────────────────────────────────────────────────────
// Read from <project root>/secrets.properties (git-ignored), falling back to
// environment variables of the same name (for CI). Copy
// secrets.properties.example to secrets.properties and fill it in.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun secret(name: String): String? =
    (secrets.getProperty(name) ?: System.getenv(name))?.trim()?.takeIf { it.isNotEmpty() }

fun requireSecret(name: String): String = secret(name) ?: throw GradleException(
    "Missing $name. Add it to secrets.properties (see secrets.properties.example) " +
        "or set it as an environment variable."
)

/** A base URL as a Java string literal, always ending in "/" (Retrofit needs it). */
fun urlField(name: String): String = "\"" + requireSecret(name).trimEnd('/') + "/\""

android {
    namespace = "in.mohammad.ramiz.islamic.kasrat_e_darrod"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "in.mohammad.ramiz.islamic.kasrat_e_darrod"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Server addresses come from secrets.properties, not the source code.
        buildConfigField("String", "API_BASE_URL", urlField("API_BASE_URL"))
        buildConfigField("String", "ALADHAN_BASE_URL", urlField("ALADHAN_BASE_URL"))

        // TLS pins for the API host (SPKI SHA-256, comma-separated). Default:
        // Let's Encrypt's two roots (ISRG Root X1 + X2), so renewing the
        // server certificate never breaks the app. "off" disables pinning.
        val pins = secret("API_CERT_PINS")
            ?: "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=,sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="
        buildConfigField("String", "API_CERT_PINS", "\"" + pins + "\"")
        // SHA-256 of the release signing certificate ("AA:BB:..."). When set,
        // a copy re-signed by someone else (a modded APK) refuses to run.
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"" + (secret("RELEASE_CERT_SHA256") ?: "") + "\"")
    }

    // Release signing, only when the keystore details are in secrets.properties.
    val releaseStore = secret("RELEASE_STORE_FILE")
    if (releaseStore != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = requireSecret("RELEASE_STORE_PASSWORD")
                keyAlias = requireSecret("RELEASE_KEY_ALIAS")
                keyPassword = requireSecret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            // R8: shrink + obfuscate release builds. Keep rules for the
            // Gson DTOs live in src/main/keepRules/rules.keep.
            optimization {
                enable = true
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.core.splashscreen)

    // UI
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.fragment)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.glide)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Google Sign-In
    implementation(libs.play.services.auth)

    // Firebase Cloud Messaging (push notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Image cropping (profile photo)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}