plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("androidApplicationPlugin") {
            id = "com.android.application"
            implementationClass = "com.android.AndroidApplicationPlugin"
        }
        create("androidLibraryPlugin") {
            id = "com.android.library"
            implementationClass = "com.android.AndroidLibraryPlugin"
        }
    }
}
