/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r42

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection

@TargetGradleVersion(">=4.2")
class BuildProgressTaskActionsCrossVersionSpec extends ToolingApiSpecification {

    ProgressEvents events

    def setup() {
        events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'root'"
    }

    //This is not a task action, but it is logged on the same level as the actions
    def "clean stale output action has an informative name"() {
        given:
        buildFile << """
            apply plugin: 'base'

            task custom {
                outputs.dir 'build/outputs'
                doLast {} }
        """.stripIndent()

        file('build/outputs/some-stale-file').text = "Stale file"

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.child('Clean stale outputs')
    }

    //This is the current behavior. Snapshotting might become not-a-task-action in the future.
    // NOTE: This operation captures more than just snapshotting, and will be removed in a later release in favor of separate snapshotting operations
    def "snapshot task inputs action has an informative name"() {
        given:
        buildFile << "task custom { doLast {} }"
        propertiesFile << "org.gradle.caching=true"

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.child('Snapshot task inputs for :custom')
    }

    @TargetGradleVersion(">=6.0")
    def "snapshot task inputs and outputs operations have an informative names"() {
        given:
        file("input.txt").createFile()
        buildFile << """
            task custom {
                inputs.file("input.txt")
                outputs.file("output.txt")
                doLast {
                    file("output.txt").text = "output"
                }
            }
        """
        propertiesFile << "org.gradle.caching=true"

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.descendant("Snapshot inputs and outputs before executing task ':custom'")
        task.descendant("Snapshot outputs after executing task ':custom'")
    }

    def "task actions implemented in annotated methods are named after the method"() {
        given:
        buildFile << """
        task custom(type: CustomTask)

        class CustomTask extends DefaultTask {
            @TaskAction void doSomethingAmazing() { }
        }
        """

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.descendant('Execute doSomethingAmazing for :custom')
    }

    def "task actions defined in doFirst and doLast blocks of Groovy build scripts have informative names"() {
        given:
        buildFile << """
            task custom {
                doFirst {}
                doLast {}
            }
        """

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.descendant('Execute doFirst {} action for :custom')
        task.descendant('Execute doLast {} action for :custom')
    }

    @TargetGradleVersion(">=4.10.3")
    def "task actions defined in doFirst and doLast blocks of Kotlin build scripts have informative names"() {
        given:
        buildFileKts << """
            tasks { create("custom") {
                doFirst {}
                doLast {}
            }}
        """

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.descendant('Execute doFirst {} action for :custom')
        task.descendant('Execute doLast {} action for :custom')
    }

    def "task actions defined in doFirst and doLast blocks of Groovy build scripts can be named"() {
        given:
        buildFile << """
            task custom {
                doFirst("A first step") {}
                doLast("One last thing...") {}
            }
        """

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.descendant('Execute A first step for :custom')
        task.descendant('Execute One last thing... for :custom')
    }

    @TargetGradleVersion(">=4.10.3")
    def "task actions defined in doFirst and doLast blocks of Kotlin build scripts can be named"() {
        given:
        buildFileKts << """
            tasks { create("custom") {
                doFirst("A first step") {}
                doLast("One last thing...") {}
            }}
        """

        when:
        runCustomTask()

        then:
        def task = events.operation("Task :custom")
        task.descendant('Execute A first step for :custom')
        task.descendant('Execute One last thing... for :custom')
    }

    private runCustomTask() {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().
                    forTasks('custom').addProgressListener(events).run()
        }
    }
}
