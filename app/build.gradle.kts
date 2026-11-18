/**
 * Construcción del módulo :app de R-Saldos.
 *
 * - Firebase (Authentication, Firestore y Storage) se configura con el archivo
 *   app/google-services.json que se descarga desde la consola de Firebase.
 * - La llave pública de ePayco y el modo de pruebas se leen de gradle.properties
 *   y se exponen en BuildConfig para no dejarlos quemados en el código.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// El plugin de Google Services solo se aplica si existe google-services.json;
// así el proyecto compila aunque todavía no se haya conectado a Firebase.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

/** Lee una propiedad de gradle.properties o devuelve un valor por defecto. */
fun prop(name: String, default: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "com.rsaldos.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rsaldos.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"

        buildConfigField("String", "EPAYCO_PUBLIC_KEY", "\"${prop("EPAYCO_PUBLIC_KEY", "")}\"")
        buildConfigField("boolean", "EPAYCO_TEST", prop("EPAYCO_TEST", "true"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Firebase: autenticación, base de datos Firestore y almacenamiento de imágenes.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Google Play Services: ubicación del dispositivo (Fused Location Provider).
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Carga de imágenes desde Firebase Storage.
    implementation("io.coil-kt:coil:2.6.0")
}
