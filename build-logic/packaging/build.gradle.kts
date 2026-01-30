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

    implementation(buildLibs.kgp)

    implementation(buildLibs.gson)
    implementation(libs.asm)

    testImplementation(testLibs.junitJupiter)
}
