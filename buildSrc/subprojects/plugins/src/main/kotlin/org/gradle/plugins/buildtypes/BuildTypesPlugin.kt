package org.gradle.plugins.buildtypes

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.*


class BuildTypesPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
    }
}


private
fun Task.isActive() = project.gradle.startParameter.taskNames.contains(name)


fun Task.projectProperty(pair: Pair<String, Any>) {
    val propertyName = pair.first
    val value = pair.second
    if (isActive()) {
        when {
            hasProperty(propertyName) -> setProperty(propertyName, value)
            else -> extra.set(propertyName, value)
        }
    }
}
