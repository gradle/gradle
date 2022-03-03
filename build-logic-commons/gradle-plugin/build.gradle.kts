plugins {
    `kotlin-dsl`
}

description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":code-quality"))
    implementation(project(":build-scan"))

    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:2.2.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0-dev-1904")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.7.0")
}
