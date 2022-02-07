subprojects {
    group = "gradlebuild"

    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension>("java") {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

description = "Provides a set of plugins that are shared between the Gradle and build-logic builds"

tasks.register("check") {
    dependsOn(subprojects.map { "${it.name}:check" })
}
