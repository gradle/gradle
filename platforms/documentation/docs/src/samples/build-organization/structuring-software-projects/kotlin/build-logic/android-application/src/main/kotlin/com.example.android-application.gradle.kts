plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

group = "com.example.myproduct"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

android {
    compileSdk = 31
    defaultConfig {
        minSdk = 26
        targetSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform("com.example.platform:product-platform"))
    testImplementation(platform("com.example.platform:test-platform"))

    implementation(kotlin("stdlib"))
}
