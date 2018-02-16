package org.gradle.plugins.buildtypes


typealias ProjectProperties = Map<String, Any>


class BuildType(val name: String) {

    init {
        require(name.isNotEmpty())
    }

    val abbreviation = abbreviationOf(name)

    var tasks: List<String> = emptyList()

    var active = false

    var onProjectProperties: (ProjectProperties) -> Unit = {}

    fun tasks(vararg tasks: String) {
        this.tasks = tasks.asList()
    }

    fun projectProperties(projectProperties: ProjectProperties) {
        if (active) onProjectProperties(projectProperties)
    }
}


private
fun abbreviationOf(name: String) =
    name[0] + name.substring(1).replace(lowercaseAlphaRegex, "")


private
val lowercaseAlphaRegex = Regex("\\p{Lower}")
