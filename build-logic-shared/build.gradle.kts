description = "Provides code shared between the Gradle runtime and build-logic building Gradle"

tasks.register("check") {
    dependsOn(subprojects.map { "${it.name}:check" })
}
