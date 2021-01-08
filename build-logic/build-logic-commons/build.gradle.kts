tasks.register("check") {
    dependsOn(subprojects.map { "${it.name}:check" })
}
