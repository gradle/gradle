plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.2"
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(30)
    }
    flavorDimensions("org.gradle.example.my-own-flavor")
    productFlavors {
        create("demo") {
            setDimension("org.gradle.example.my-own-flavor")
        }
        create("full") {
            setDimension("org.gradle.example.my-own-flavor")
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
