kotlinDslPlugin {
    description = "Provides plugins that combine and configure other plugins for different kinds of Gradle subprojects"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")
        implementation("gradlebuild:publishing")

        implementation(project(":buildquality"))
        implementation(project(":cleanup"))
        implementation(project(":dependency-modules"))
        implementation(project(":jvm"))
        implementation(project(":packaging"))
        implementation(project(":profiling"))

        implementation(catalog("buildLibs.kgp"))
        // FIXME: cannot use catalog() + action-taking dependency notation
        implementation("com.autonomousapps:dependency-analysis-gradle-plugin:3.4.0") {
            exclude(mapOf("group" to "com.google.j2objc", "module" to "j2objc-annotations"))
        }
    }
}
