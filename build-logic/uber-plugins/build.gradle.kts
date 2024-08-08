plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins that combine and configure other plugins for different kinds of Gradle subprojects"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:publishing")

    implementation(projects.buildquality)
    implementation(projects.cleanup)
    implementation(projects.dependencyModules)
    implementation(projects.jvm)
    implementation(projects.profiling)

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin")
}
