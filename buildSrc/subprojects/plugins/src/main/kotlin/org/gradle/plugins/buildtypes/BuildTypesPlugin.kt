package org.gradle.plugins.buildtypes

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*


class BuildTypesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val buildTypes = container(BuildType::class)
        extensions.add("buildTypes", buildTypes)
        buildTypes.all {
            register(this)
            val activeBuildTypes = buildTypes.filter { it.active }
            require(activeBuildTypes.size <= 1) {
                "Only one build type can be active. Active build types: ${activeBuildTypes.joinToString(", ") { it.name }}"
            }
        }
    }

    private
    fun Project.register(buildType: BuildType) {
        subprojects {
            afterEvaluate {
                val buildTypeTask = tasks.register(buildType.name) {
                    group = "Build Type"
                    description = "The $name build type (can only be abbreviated to '${buildType.abbreviation}')"
                }

                buildType.tasks.reversed().forEach { taskName ->
                    if (taskName.startsWith(":")) {
                        buildTypeTask.configure { dependsOn(taskName) }
                    } else {
                        val task = tasks.findByPath(taskName)
                        if (task != null) {
                            buildTypeTask.configure { dependsOn(task) }
                        }
                    }
                }
            }
        }


        val invokedTaskNames = gradle.startParameter.taskNames
        val usedTasks = buildType.findUsedTaskNamesWithIndexIn(invokedTaskNames)
        usedTasks.forEach { (_, usedName) ->
            require(usedName.isNotEmpty())
            buildType.active = true
            buildType.onProjectProperties = { properties: ProjectProperties ->
                properties.forEach { (name, value) ->
                    project.setOrCreateProperty(name, value)
                }
            }
        }
    }

    private
    fun BuildType.findUsedTaskNamesWithIndexIn(taskNames: List<String>): List<IndexedValue<String>> {
        val candidates = arrayOf(name, abbreviation)
        val nameSuffix = ":$name"
        val abbreviationSuffix = ":$abbreviation"
        return taskNames.withIndex().filter { (index, taskName) ->
            (taskName in candidates || taskName.endsWith(nameSuffix) || taskName.endsWith(abbreviationSuffix)) && !isTaskHelpInvocation(taskNames, index)
        }
    }

    private
    fun isTaskHelpInvocation(taskNames: List<String>, taskIndex: Int) =
        taskIndex >= 2
            && taskNames[taskIndex - 1] == "--task"
            && taskNames[taskIndex - 2].let(helpTaskRegex::matches)

    private
    val helpTaskRegex = Regex("h(e(lp?)?)?")

    private
    fun Project.setOrCreateProperty(propertyName: String, value: Any) {
        when {
            hasProperty(propertyName) -> setProperty(propertyName, value)
            else -> extra.set(propertyName, value)
        }
    }
}
