import com.android.build.api.dsl.ApplicationBaseFlavor
import com.android.build.api.dsl.ApplicationVariantDimension

plugins {
    id("com.android.library")
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
    flavorDimensions.add("org.gradle.example.my-own-flavor")
    productFlavors {
        create("demo") {
            dimension = "org.gradle.example.my-own-flavor"
            (this as ApplicationVariantDimension).versionNameSuffix = "-demo"
        }
        create("full") {
            dimension = "org.gradle.example.my-own-flavor"
            (this as ApplicationVariantDimension).versionNameSuffix = "-full"
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
