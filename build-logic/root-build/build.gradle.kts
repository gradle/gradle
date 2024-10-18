plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins that configures the root Gradle project"

dependencies {
    implementation("gradlebuild:basics")

    implementation(projects.idea)
    implementation(projects.profiling)

    implementation(projects.cleanup) {
        because("The CachesCleaner service is shared and needs to be on the root classpath")
    }

    implementation("com.autonomousapps:dependency-analysis-gradle-plugin") {
        exclude(group = "com.google.j2objc", module = "j2objc-annotations") // This has no use in Gradle
    }
}
