plugins {
    id("com.android.library")
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

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("lib") {
                from(components["all"])
            }
        }
    }
}
