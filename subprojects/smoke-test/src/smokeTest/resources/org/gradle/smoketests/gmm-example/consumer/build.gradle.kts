plugins {
    id("com.android.application") version "$androidPluginVersion" apply false
    kotlin("jvm") version "$kotlinVersion" apply false
    kotlin("android") version "$kotlinVersion" apply false
    kotlin("android.extensions") version "$kotlinVersion" apply false
    kotlin("multiplatform") version "$kotlinVersion" apply false
}

allprojects {
    repositories {
        maven {
            setUrl(File(rootDir.parentFile, "producer/repo"))
        }
        jcenter()
        google()
    }
}
