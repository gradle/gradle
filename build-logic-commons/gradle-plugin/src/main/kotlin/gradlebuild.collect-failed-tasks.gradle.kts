/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import gradlebuild.AbstractBuildScanInfoCollectingService
import gradlebuild.registerBuildScanInfoCollectingService
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskOperationResult
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask
import java.io.Serializable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Register a build service that monitors compilation tasks and code quality tasks (Checkstyle/CodeNarc/ktlint)
 * and reports them as TeamCity build problems.
 */
registerBuildScanInfoCollectingService(CollectFailedTaskPathsBuildService::class.java, ::shouldBeReportedAsTeamCityBuildProblem) { failedTasksInBuildLogic, failedTasksInMainBuild ->
    buildScanPublished {
        ((failedTasksInBuildLogic as List<*>) + (failedTasksInMainBuild as List<*>)).forEach {
            println("##teamcity[buildProblem description='${buildScanUri}/console-log?task=$it']")
        }
    }
}

fun shouldBeReportedAsTeamCityBuildProblem(task: Task) = task is Checkstyle || task is GenerateReportsTask || task is AbstractCompile || task is CodeNarc

abstract class CollectFailedTaskPathsBuildService : AbstractBuildScanInfoCollectingService() {
    private val failedTaskPaths = CopyOnWriteArrayList<String>()
    override val collectedInformation: Serializable = failedTaskPaths

    override fun shouldInclude(taskPath: String): Boolean {
        // https://github.com/gradle/gradle/issues/21351
        return super.shouldInclude(taskPath) || taskPath.contains("ktlintMainSourceSetCheck")
    }

    override fun action(taskPath: String, taskResult: TaskOperationResult) {
        if (taskResult is TaskFailureResult) {
            failedTaskPaths.add(taskPath)
        }
    }
}
