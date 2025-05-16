plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

dependencies {
    compileOnly("com.gradle:develocity-gradle-plugin")

    api(platform(projects.buildPlatform))

    implementation(projects.basics)
    implementation(projects.moduleIdentity)
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.1.0")

    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:6.1.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")
    implementation("org.gradle:test-retry-gradle-plugin:1.5.2")

    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
}
