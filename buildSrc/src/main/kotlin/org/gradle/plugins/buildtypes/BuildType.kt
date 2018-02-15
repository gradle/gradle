package org.gradle.plugins.buildtypes


class BuildType(val name: String) {

    val abbreviation = name[0] + name.substring(1).replace(lowercaseAlphaRegex, "")

    var tasks: List<String> = emptyList()
    var projectProperties: Map<String, Any> = emptyMap()
    var active = true
    var propertiesAction: (Map<String, Any>) -> Unit = {}

    fun tasks(vararg tasks: String) {
        this.tasks = tasks.asList()
    }

    fun projectProperties(projectProperties: Map<String, Any>) {
        this.projectProperties = projectProperties
        if (active) {
            propertiesAction(projectProperties)
        }
    }
}


private
val lowercaseAlphaRegex = Regex("[a-z]")
