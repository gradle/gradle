plugins {
    id("com.android.library")
    kotlin("android")
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
}

dependencies {
    implementation(kotlin("stdlib"))
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