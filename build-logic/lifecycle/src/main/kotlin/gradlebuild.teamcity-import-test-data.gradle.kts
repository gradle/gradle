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
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.work.DisableCachingByDefault
import gradlebuild.basics.repoRoot
import gradlebuild.basics.BuildEnvironment.isTeamCity

/**
 * This is a workaround for https://youtrack.jetbrains.com/issue/TW-76894.
 *
 * In short, we want TeamCity to be aware of the test execution data (which tests are executed and how long they are),
 * even when the Test task is `FROM-CACHE` or `UP-TO-DATE`. This build service will output a service message to instruct TeamCity to read JUnit test result XMLs.
 *
 * See https://www.jetbrains.com/help/teamcity/service-messages.html#Importing+XML+Reports
 */
@DisableCachingByDefault(because = "It outputs a TeamCity service message")
abstract class EmitTeamCityImportDataServiceMessageBuildService : BuildService<EmitTeamCityImportDataServiceMessageBuildService.Params>, OperationCompletionListener {
    interface Params : BuildServiceParameters {
        /**
         * Key is the path of test task, value is the path (relative to repo root) of the output JUnit XML directory.
         */
        val testTaskPathToJUnitXmlLocation: MapProperty<String, String>
    }

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) {
            return
        }

        val taskPath = event.descriptor.taskPath
        val outputXmlPath = parameters.testTaskPathToJUnitXmlLocation.get().get(taskPath) ?: return

        val taskResult = event.result

        if (taskResult is TaskSuccessResult && (taskResult.isFromCache || taskResult.isUpToDate)) {
            println("##teamcity[importData type='junit' path='$outputXmlPath/TEST-*.xml' verbose='true']")
        }
    }
}

if (isTeamCity) {
    val gradleRootDir = repoRoot().asFile.toPath()
    project.gradle.taskGraph.whenReady {
        val buildService: Provider<EmitTeamCityImportDataServiceMessageBuildService> = gradle.sharedServices.registerIfAbsent("emitTeamCityImportDataServiceMessageBuildService-$name", EmitTeamCityImportDataServiceMessageBuildService::class.java) {
            parameters.testTaskPathToJUnitXmlLocation = allTasks.filterIsInstance<Test>().associate {
                it.path to gradleRootDir.relativize(it.reports.junitXml.outputLocation.asFile.get().toPath()).toString()
            }
        }
        gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(buildService)
    }
}
