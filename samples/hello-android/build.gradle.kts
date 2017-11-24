plugins {
    id("com.android.application") version "3.0.1"
    kotlin("android") version "1.1.61"
}

android {
    buildToolsVersion("27.0.1")
    compileSdkVersion(27)

    defaultConfig {
        minSdkVersion(15)
        targetSdkVersion(27)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    compile("com.android.support:appcompat-v7:27.0.1")
    compile("com.android.support.constraint:constraint-layout:1.0.2")
    compile(kotlin("stdlib", "1.1.61"))
}

repositories {
    jcenter()
    google()
}
