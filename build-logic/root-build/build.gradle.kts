plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins that configures the root Gradle project"

dependencies {
    implementation("gradlebuild:basics")

    implementation(project(":idea"))
    implementation(project(":profiling"))

    implementation(project(":cleanup")) {
        because("The CachesCleaner service is shared and needs to be on the root classpath")
    }

    implementation("com.autonomousapps:dependency-analysis-gradle-plugin")
}
