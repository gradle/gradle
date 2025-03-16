plugins {
    id("java-platform")
}

group = "com.example.platform"

dependencies {
    constraints {
        api("com.android.tools.build:gradle:7.3.1")
        api("org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:2.0.21")
        api("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.0.21")
        api("org.springframework.boot:org.springframework.boot.gradle.plugin:2.7.8")
    }
}
