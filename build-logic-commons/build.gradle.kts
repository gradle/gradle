description = "Provides a set of plugins that are shared between the Gradle and build-logic builds"

tasks.register("check") {
    dependsOn(subprojects.map { "${it.name}:check" })
}
