rootProject.name = "project-evaluate-events"
include("project-a", "project-b")

project(":project-a").buildFileName = "../project-a.gradle.kts"
