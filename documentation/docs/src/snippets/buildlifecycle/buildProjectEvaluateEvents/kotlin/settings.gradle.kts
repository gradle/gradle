rootProject.name = "build-project-evaluate-events"

include("project-a", "project-b")

project(":project-b").buildFileName = "../project-b.gradle.kts"
