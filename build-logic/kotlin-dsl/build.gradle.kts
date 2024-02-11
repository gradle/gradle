plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure Kotlin DSL and patch the Kotlin compiler for use in Kotlin subprojects"

dependencies {
    implementation("gradlebuild:basics")

    implementation(project(":dependency-modules"))
    implementation(project(":jvm"))
    implementation(project(":kotlin-dsl-shared-runtime"))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("sam-with-receiver"))
    implementation("org.ow2.asm:asm")
    implementation("com.thoughtworks.qdox:qdox")

    testImplementation("junit:junit")
    testImplementation("com.nhaarman:mockito-kotlin")
}
