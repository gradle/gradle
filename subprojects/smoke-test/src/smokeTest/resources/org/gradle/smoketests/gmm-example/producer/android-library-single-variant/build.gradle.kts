plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.2"
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(30)
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
                from(components["fullRelease"])
            }
        }
    }
}
