package org.gradle.plugins.buildtypes

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra

class BuildTypesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val buildTypesContainer = project.container(BuildType::class.java)
        project.extensions.add("buildTypes", buildTypesContainer)
        buildTypesContainer.all {
            register(this, project)
        }
    }

    fun register(buildType: BuildType, project: Project) {
        project.tasks.create(buildType.name) {
            group = "Build Type"
            val abbreviation = name[0] + name.substring(1).replace(Regex("[a-z]"), "")
            val taskNames = project.gradle.startParameter.taskNames
            val usedName = taskNames.find { it in listOf(name, abbreviation) || it.endsWith(":$name") || it.endsWith(":$abbreviation") }
            val index = taskNames.indexOf(usedName)
            if (usedName != null && !usedName.isEmpty() &&
                !((index > 0) && (taskNames[index - 1] == "--task") &&
                    ((index > 1) && Regex("h(e(lp?)?)?").matches(taskNames[index - 2])))) {
                val subproject =
                    if (usedName.contains(":")) usedName.substring(0, usedName.lastIndexOf(":") + 1)
                    else ""
                project.afterEvaluate {
                    taskNames.removeAt(index)
                    if (subproject.isEmpty() || project.findProject(subproject) != null) {
                        buildType.tasks.reversed().forEach {
                            val path = subproject + it
                            if (subproject.isEmpty() || project.tasks.findByPath(path) != null) {
                                taskNames.add(index, path)
                            } else {
                                println("Skipping task '$path' requested by build type $name, as it does not exist.")
                            }
                        }
                    } else {
                        println("Skipping execution of build type '${buildType.name}'. Project '$subproject' not found in root project '${project.name}'.")
                    }
                    if (taskNames.isEmpty()) {
                        taskNames.add("help") //do not trigger the default tasks
                    }
                    project.gradle.startParameter.setTaskNames(taskNames)
                }
                buildType.propertiesAction = { properties: Map<String, Any> ->
                    properties.forEach { key, value ->
                        project.extra.set(key, value)
                    }
                }
            }
            doFirst {
                throw GradleException("'$name' is a build type and has to be invoked directly, and its name can only be abbreviated to '$abbreviation'.")
            }
        }
    }

}
