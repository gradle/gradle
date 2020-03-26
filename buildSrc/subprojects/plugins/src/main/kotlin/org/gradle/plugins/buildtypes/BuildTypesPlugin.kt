package org.gradle.plugins.buildtypes

import org.gradle.api.GradleException
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
        tasks.register(buildType.name) {

            group = "Build Type"

            description = "The $name build type (can only be abbreviated to '${buildType.abbreviation}')"

            doFirst {
                throw GradleException("'$name' is a build type and must be invoked directly, and its name can only be abbreviated to '${buildType.abbreviation}'.")
            }
        }

        val invokedTaskNames = gradle.startParameter.taskNames
        val usedTaskNames = buildType.findUsedTaskNamesWithIndexIn(invokedTaskNames).reversed()
        usedTaskNames.forEach { (_, usedName) ->
            require(usedName.isNotEmpty())
            buildType.active = true
            buildType.onProjectProperties = { properties: ProjectProperties ->
                properties.forEach { (name, value) ->
                    project.setOrCreateProperty(name, value)
                }
            }
        }
        if (usedTaskNames.isNotEmpty()) {
            afterEvaluate {
                usedTaskNames.forEach { (index, usedName) ->
                    invokedTaskNames.removeAt(index)

                    val subproject = usedName.substringBeforeLast(":", "")
                    insertBuildTypeTasksInto(invokedTaskNames, index, buildType, subproject)

                    gradle.startParameter.setTaskNames(invokedTaskNames)
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
}


internal
fun Project.insertBuildTypeTasksInto(
    taskList: MutableList<String>,
    index: Int,
    buildType: BuildType,
    subproject: String
) {

    fun insert(task: String) =
        taskList.add(index, task)

    fun forEachBuildTypeTask(act: (String) -> Unit) =
        buildType.tasks.reversed().forEach(act)

    when {
        subproject.isEmpty() ->
            forEachBuildTypeTask(::insert)

        findProject(subproject) != null ->
            forEachBuildTypeTask {
                val taskPath = "$subproject:$it"
                when {
                    tasks.findByPath(taskPath) == null -> println("Skipping task '$taskPath' requested by build type ${buildType.name}, as it does not exist.")
                    else -> insert(taskPath)
                }
            }
        else -> {
            println("Skipping execution of build type '${buildType.name}'. Project '$subproject' not found in root project '$name'.")
        }
    }

    if (taskList.isEmpty()) {
        taskList.add("help") // do not trigger the default tasks
    }
}


fun Project.setOrCreateProperty(propertyName: String, value: Any) {
    when {
        hasProperty(propertyName) -> setProperty(propertyName, value)
        else -> extra.set(propertyName, value)
    }
}
