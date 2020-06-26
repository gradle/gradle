plugins {
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("com.gradle.plugin-publish") version "0.12.0"
    id("maven-publish")
}

rootProject.group = "org.gradle.enterprise"
rootProject.version = "0.1-SNAPSHOT"

repositories {
    jcenter()
    gradlePluginPortal()
}

dependencies {
    val gradleEnterprisePluginVersion = "3.3.4"
    compileOnly("com.gradle:gradle-enterprise-gradle-plugin:${gradleEnterprisePluginVersion}")
    implementation(gradleApi())
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

gradlePlugin {
    val helper by plugins.creating {
        id = "org.gradle.enterprise.gradle-enterprise-conventions-plugin"
        implementationClass = "org.gradle.enterprise.GradleEnterpriseConventionsPlugin"
    }
}

