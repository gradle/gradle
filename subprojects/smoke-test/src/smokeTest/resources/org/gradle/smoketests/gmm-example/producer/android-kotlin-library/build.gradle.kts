import com.android.build.api.dsl.ApplicationBaseFlavor

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = 30
    buildToolsVersion = "30.0.2"
    defaultConfig {
        minSdk = 16
        targetSdk = 30
        (this as ApplicationBaseFlavor).versionCode = 1
        (this as ApplicationBaseFlavor).versionName = "1.0"
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
