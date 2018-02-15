package org.gradle.plugins.buildtypes

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*


class BuildTypesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val buildTypesContainer = container(BuildType::class.java)
        extensions.add("buildTypes", buildTypesContainer)
        buildTypesContainer.all {
            register(this)
        }
    }

    private
    fun Project.register(buildType: BuildType) =
        tasks.create(buildType.name) {
            group = "Build Type"
            val taskNames = gradle.startParameter.taskNames
            val usedName = buildType.findUsedTaskNameFrom(taskNames)
            val index = taskNames.indexOf(usedName)
            if (usedName != null && usedName.isNotEmpty() && isNotHelpTaskInvocation(usedName, index, taskNames)) {
                afterEvaluate {
                    taskNames.removeAt(index)
                    insertBuildTypeTaskNames(buildType, usedName, index, taskNames)
                    gradle.startParameter.setTaskNames(taskNames)
                }
                buildType.propertiesAsExtraOn(project)
            }
            doFirst {
                throw GradleException("'$name' is a build type and has to be invoked directly, and its name can only be abbreviated to '${buildType.abbreviation}'.")
            }
        }


    private
    fun BuildType.findUsedTaskNameFrom(taskNames: List<String>): String? {
        val candidates = listOf(name, abbreviation)
        val nameSuffix = ":$name"
        val abbreviationSuffix = ":$abbreviation"
        return taskNames.find { it in candidates || it.endsWith(nameSuffix) || it.endsWith(abbreviationSuffix) }
    }


    private
    val helpTaskRegex = Regex("h(e(lp?)?)?")


    private
    fun isNotHelpTaskInvocation(usedName: String, index: Int, taskNames: List<String>) =
        !(index > 0 && taskNames[index - 1] == "--task" && index > 1 && helpTaskRegex.matches(taskNames[index - 2]))


    private
    fun Project.insertBuildTypeTaskNames(buildType: BuildType, usedName: String, index: Int, taskNames: MutableList<String>) {
        val subproject = usedName.substringBeforeLast(":", "")
        if (subproject.isEmpty() || findProject(subproject) != null) {
            buildType.tasks.reversed().forEach {
                val path = subproject + it
                if (subproject.isEmpty() || tasks.findByPath(path) != null) {
                    taskNames.add(index, path)
                } else {
                    println("Skipping task '$path' requested by build type $name, as it does not exist.")
                }
            }
        } else {
            println("Skipping execution of build type '${buildType.name}'. Project '$subproject' not found in root project '$name'.")
        }
        if (taskNames.isEmpty()) {
            taskNames.add("help") //do not trigger the default tasks
        }
    }


    private
    fun BuildType.propertiesAsExtraOn(project: Project) {
        propertiesAction = { properties: Map<String, Any> ->
            properties.forEach { key, value ->
                project.extra.set(key, value)
            }
        }
    }
}
