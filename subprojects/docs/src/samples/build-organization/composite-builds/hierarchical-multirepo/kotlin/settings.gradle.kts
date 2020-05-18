rootProject.name = "multirepo-app"
// tag::include_builds[]
file("modules").listFiles().forEach { moduleBuild: File ->
    includeBuild(moduleBuild)
}
// end::include_builds[]
