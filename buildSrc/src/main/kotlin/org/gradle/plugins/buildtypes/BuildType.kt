package org.gradle.plugins.buildtypes

class BuildType(val name: String) {
    var tasks: List<String> = emptyList()
    var projectProperties: Map<String,Any> = emptyMap()
    var active = true
    var propertiesAction = { properties: Map<String,Any> ->  }

    fun tasks(vararg tasks: String) {
        this.tasks = tasks.asList()
    }

    fun projectProperties(projectProperties: Map<String,Any>) {
        this.projectProperties = projectProperties
        if (active) {
            propertiesAction(projectProperties)
        }
    }
}


