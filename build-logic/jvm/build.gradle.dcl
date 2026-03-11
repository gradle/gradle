kotlinDslPlugin {
    description = "Provides plugins to configure conventions used by Kotlin, Java and Groovy projects to build Gradle"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(project(":dependency-modules"))

        implementation(catalog("buildLibs.develocityPlugin"))
        implementation(catalog("buildLibs.kgp"))
    }
}
