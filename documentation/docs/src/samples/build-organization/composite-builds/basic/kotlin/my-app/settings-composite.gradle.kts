// Needs to be used with --settings-file settings-composite.gradle.kts

rootProject.name = "my-app"

include("app")

includeBuild("../my-utils")
