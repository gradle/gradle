plugins {
    id("com.android.application") version "2.3.3"
    kotlin("android") version "1.1.50"
}

android {
    buildToolsVersion("25.0.0")
    compileSdkVersion(23)

    defaultConfig {
        minSdkVersion(15)
        targetSdkVersion(23)

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
    compile("com.android.support:appcompat-v7:23.4.0")
    compile("com.android.support.constraint:constraint-layout:1.0.0-alpha8")
    compile(kotlin("stdlib"))
}

repositories {
    jcenter()
}
