plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure conventions used by Kotlin, Java and Groovy projects to build Gradle"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.dependencyModules)

    implementation(buildLibs.develocityPlugin)
    implementation(buildLibs.kgp)
}
