plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion(29)
    buildToolsVersion = "29.0.2"
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions("org.gradle.example.my-own-flavor")
    productFlavors {
        create("demo") {
            setDimension("org.gradle.example.my-own-flavor")
            versionNameSuffix = "-demo"
        }
        create("full") {
            setDimension("org.gradle.example.my-own-flavor")
            versionNameSuffix = "-full"
        }
    }

}

kotlin {
    // jvm()
    js()
    macosX64()
    linuxX64()
    android {
        publishLibraryVariants("fullRelease", "demoRelease",
                "fullDebug", "demoDebug")
    }
}

dependencies {
    "commonMainImplementation"(kotlin("stdlib-common"))
    // "jvmMainImplementation"(kotlin("stdlib"))
    "androidMainImplementation"(kotlin("stdlib"))
    "jsMainImplementation"(kotlin("stdlib-js"))
}

afterEvaluate {
    publishing {
        publications.forEach { println("Koltin-Native publication: ${it.name}") }
    }
}