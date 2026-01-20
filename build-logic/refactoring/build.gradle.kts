plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to help refactor Gradle projects"

dependencies {
    implementation("gradlebuild:basics")
    implementation("org.ow2.asm:asm")
}
