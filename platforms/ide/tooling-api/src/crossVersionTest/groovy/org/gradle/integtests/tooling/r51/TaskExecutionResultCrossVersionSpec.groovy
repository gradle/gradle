/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r51

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.util.GradleVersion

@TargetGradleVersion('>=5.1')
class TaskExecutionResultCrossVersionSpec extends ToolingApiSpecification {

    def events = ProgressEvents.create()

    def "reports no execution reasons for skipped tasks"() {
        given:
        buildFile << """
            task disabledTask {
                enabled = false
            }
        """

        when:
        runBuild('disabledTask')

        then:
        taskSkippedResult(':disabledTask')
    }

    def "reports execution reason for executed task without declared outputs"() {
        given:
        buildFile << """
            task helloWorld {
                doFirst { println "Hello, World!" }
            }
        """

        when:
        runBuild('helloWorld')

        then:
        with (taskSuccessResult(':helloWorld')) {
            !incremental
            executionReasons == ["Task has not declared any outputs despite executing actions."]
        }
    }

    def "reports empty list of execution reasons for up-to-date tasks"() {
        given:
        buildFile << """
            task writeFile {
                def file = file("\$buildDir/test.txt")
                outputs.file(file)
                doFirst {
                    file.createNewFile()
                    file.text == "Hello, World!"
                }
            }
        """

        when:
        runBuild('writeFile')

        then:
        with (taskSuccessResult(':writeFile')) {
            !upToDate
            !executionReasons.empty
        }

        when:
        runBuild('writeFile')

        then:
        with (taskSuccessResult(':writeFile')) {
            upToDate
            executionReasons.empty
        }
    }

    def "reports execution reasons for failed tasks"() {
        given:
        buildFile << """
            task failingTask {
                doFirst {
                    throw new GradleException('task failed intentionally')
                }
            }
        """

        when:
        runBuild('failingTask')

        then:
        thrown(BuildException)
        with (taskFailureResult(':failingTask')) {
            !executionReasons.empty
            failures[0].description.contains("task failed intentionally")
        }
    }

    def "reports incremental task as incremental"() {
        given:
        file('src').mkdir()
        def supportsInputChanges = targetVersion > GradleVersion.version("5.4")
        def parameterType = supportsInputChanges ? 'InputChanges' : 'IncrementalTaskInputs'
        buildFile << """
            task incrementalTask(type: MyIncrementalTask) {
                inputDir = file('src')
            }

            class MyIncrementalTask extends DefaultTask {
                ${supportsInputChanges ? "@Incremental" : ""}
                @InputDirectory
                def File inputDir
                @TaskAction
                void doSomething(${parameterType} inputs) {}

                @Optional
                @OutputFile
                public File getOutputFile() {
                    return null;
                }
            }
        """

        when:
        runBuild('incrementalTask')

        then:
        with(taskSuccessResult(':incrementalTask')) {
            !incremental
            executionReasons[0].contains("No history")
        }

        when:
        file('src/a').touch()
        file('src/b').touch()

        and:
        runBuild('incrementalTask')

        then:
        with(taskSuccessResult(':incrementalTask')) {
            incremental
            executionReasons[0].contains("a has been added")
            executionReasons[1].contains("b has been added")
        }

        when:
        file('src/a').text = "changed content"
        file('src/b').delete()

        and:
        runBuild('incrementalTask')

        then:
        with(taskSuccessResult(':incrementalTask')) {
            incremental
            executionReasons[0].contains("a has changed")
            executionReasons[1].contains("b has been removed")
        }
    }

    @TargetGradleVersion('>=3.0 <5.1')
    def "throws UnsupportedMethodException for execution reasons when target version does not support it"() {
        when:
        runBuild('tasks')

        and:
        taskSuccessResult(':tasks').executionReasons

        then:
        def e = thrown(UnsupportedMethodException)
        e.message.startsWith("Unsupported method: TaskExecutionResult.getExecutionReasons().")
    }

    @TargetGradleVersion('>=3.0 <5.1')
    def "throws UnsupportedMethodException for incremental property when target version does not support it"() {
        when:
        runBuild('tasks')

        and:
        taskSuccessResult(':tasks').incremental

        then:
        def e = thrown(UnsupportedMethodException)
        e.message.startsWith("Unsupported method: TaskExecutionResult.isIncremental().")
    }

    private void runBuild(String... tasks) {
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks(tasks)
                    .addProgressListener(events, EnumSet.of(OperationType.TASK))
                    .run()
        }
    }

    private TaskSkippedResult taskSkippedResult(String path) {
        taskResult(path) as TaskSkippedResult
    }

    private TaskSuccessResult taskSuccessResult(String path) {
        taskResult(path) as TaskSuccessResult
    }

    private TaskFailureResult taskFailureResult(String path) {
        taskResult(path) as TaskFailureResult
    }

    private TaskOperationResult taskResult(String path) {
        events.operation("Task $path").result as TaskOperationResult
    }

}
