plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin for building Gradle distributions"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.documentation) {
        // TODO turn this around: move corresponding code to this project and let docs depend on it
        because("API metadata generation is part of the DSL guide")
    }
    implementation(projects.jvm)
    implementation(projects.kotlinDsl)

    implementation(project(":java-api-extractor"))

    implementation(kotlin("gradle-plugin"))

    implementation("com.google.code.gson:gson")
    implementation("org.ow2.asm:asm")

    testImplementation("org.junit.jupiter:junit-jupiter")
}
