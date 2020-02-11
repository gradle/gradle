plugins {
    id("com.android.library")
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

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("lib") {
                from(components["all"])
            }
        }
    }
}