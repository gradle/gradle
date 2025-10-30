// tag::configurations[]
plugins {
    id("com.android.application") version "8.13.0"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.kapt") version "2.1.20"
}

// end::configurations[]
repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

android {
    namespace = "com.example.helloworld"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.helloworld"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// tag::configurations[]
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
// end::configurations[]
