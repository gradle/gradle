rootProject.name = "multirepo-app"

file("modules").listFiles().forEach { moduleBuild: File ->
    includeBuild(moduleBuild)
}
