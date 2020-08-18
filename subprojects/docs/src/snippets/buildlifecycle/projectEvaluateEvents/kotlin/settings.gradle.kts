rootProject.name = "projectEvaluateEvents"
include("project-a", "project-b")

project(":project-a").buildFileName = "../project-a.gradle.kts"
