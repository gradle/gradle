plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradle:gradle-enterprise-gradle-plugin:3.10.3")
    //implementation("com.gradle:develocity-gradle-plugin:4.1")
}
