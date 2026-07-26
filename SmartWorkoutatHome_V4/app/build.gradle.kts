plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.smartworkoutathome_v4"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartworkoutathome_v4"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Mengaktifkan View Binding untuk kemudahan interaksi komponen UI Layout
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ===== ANDROID CORE UTAMA =====
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.12.0")

    // ===== ANDROID CAMERAX (UNTUK INPUT FRAME KAMERA) =====
    implementation("androidx.camera:camera-core:1.3.2")
    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")
    implementation("androidx.camera:camera-view:1.3.2")

    // ===== GOOGLE MEDIAPIPE (DETEKSI POSE & LANDMARK OLAHRAGA) =====
    implementation("com.google.mediapipe:tasks-vision:0.10.9")

    // ===== LIFECYCLE MANAGEMENT (VIEWMODEL & LIVEDATA REAL-TIME UI) =====
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // ===== PENGUJIAN UNIT (UNIT TESTING) =====
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}