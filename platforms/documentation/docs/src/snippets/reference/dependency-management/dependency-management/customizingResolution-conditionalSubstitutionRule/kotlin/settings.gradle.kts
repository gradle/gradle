rootProject.name = "customizing-resolution-conditional-substitution-rule"
val projectNames = listOf("project1", "project2", "project3")
include("consumer")

projectNames.forEach { name ->
    if (isIncluded(name)) {
        println("project $name is INTERNAL to this build")
        include(name)
    } else {
        println("project $name is external to this build")
    }
}

fun isIncluded(projectName: String): Boolean {
    if (System.getProperty("useLocal") != null) {
        val localProjects = System.getProperty("useLocal").split(",")
        return (projectName in localProjects)
    }
    return false
}
