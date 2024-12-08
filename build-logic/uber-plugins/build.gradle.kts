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
    implementation(projects.packaging)
    implementation(projects.profiling)

    implementation(kotlin("gradle-plugin"))
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin") {
        exclude(group = "com.google.j2objc", module = "j2objc-annotations") // This has no use in Gradle
    }
}
