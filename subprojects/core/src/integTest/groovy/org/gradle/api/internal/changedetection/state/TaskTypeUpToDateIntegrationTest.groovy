/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class TaskTypeUpToDateIntegrationTest extends AbstractIntegrationSpec {

    def "task is up-to-date after unrelated change to build script"() {
        file("input.txt") << "input"
        buildFile """
            task copy(type: Copy) {
                from "input.txt"
                destinationDir = buildDir
            }
        """

        when:
        succeeds "copy"
        then:
        executedAndNotSkipped(":copy")

        file('build/input.txt').makeOlder()

        when:
        succeeds "copy"
        then:
        skipped ":copy"

        buildFile << """
            task other {}
        """

        when:
        succeeds "copy"
        then:
        skipped ":copy"
    }

    def "task with type declared in build script is not up-to-date after build script change"() {
        file("input.txt") << "input"

        buildFile << declareSimpleCopyTask(false)

        when:
        succeeds "copy"
        then:
        executedAndNotSkipped(":copy")

        file("output.txt").makeOlder()

        when:
        succeeds "copy"
        then:
        skipped ":copy"

        when:
        buildFile.text = declareSimpleCopyTask(true)

        succeeds "copy"

        then:
        executedAndNotSkipped ":copy"

        when:
        succeeds "copy"

        then:
        skipped ":copy"
    }

    def "task with action declared in build script is not up-to-date after build script change"() {
        file("input.txt") << "input"
        buildFile """
            task copy(type: Copy) {
                from "input.txt"
                destinationDir = buildDir
                doLast {
                    println "Custom action"
                }
            }
        """

        when:
        succeeds "copy"
        then:
        executedAndNotSkipped ":copy"

        file('build/input.txt').makeOlder()

        when:
        succeeds "copy"
        then:
        skipped ":copy"

        when:
        buildFile << """
            task other {}
        """
        succeeds "copy"
        then:
        executedAndNotSkipped(":copy")

        when:
        succeeds "copy"
        then:
        skipped ":copy"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2936")
    def "task declared in buildSrc is not up-to-date after its source is changed"() {
        file("input.txt") << "input"

        file("buildSrc/src/main/groovy/SimpleCopyTask.groovy") << declareSimpleCopyTaskType(false)
        buildFile """
            task copy(type: SimpleCopy) {
                input = file("input.txt")
                output = file("output.txt")
            }
        """

        when:
        succeeds "copy"
        then:
        executedAndNotSkipped ":copy"

        file("output.txt").makeOlder()

        when:
        succeeds "copy"
        then:
        skipped ":copy"

        when:
        file("buildSrc/src/main/groovy/SimpleCopyTask.groovy").text = declareSimpleCopyTaskType(true)

        succeeds "copy"

        then:
        executedAndNotSkipped ":copy"

        when:
        succeeds "copy"

        then:
        skipped ":copy"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-1910")
    def "task declared in buildSrc is not up-to-date after dependencies change"() {
        file("input.txt") << "input"

        file("buildSrc/src/main/groovy/SimpleCopy.groovy") << declareSimpleCopyTaskType()

        file("buildSrc/build.gradle") << guavaDependency("15.0")

        buildFile """
            task copy(type: SimpleCopy) {
                input = file("input.txt")
                output = file("output.txt")
            }
        """

        when:
        succeeds "copy"
        then:
        executedAndNotSkipped(":copy")

        file("output.txt").makeOlder()

        when:
        succeeds "copy"
        then:
        skipped(":copy")

        when:
        file("buildSrc/build.gradle").text = guavaDependency("19.0")

        succeeds "copy"

        then:
        executedAndNotSkipped(":copy")

        when:
        succeeds "copy"

        then:
        skipped(":copy")
    }

    @Issue("https://github.com/gradle/gradle/issues/9723")
    def "declaring a task action receiving InputChanges without declaring outputs is not allowed"() {
        def input = file('input.txt').createFile()
        buildFile << """
            class IncrementalTask extends DefaultTask {
                @InputFile File input

                @TaskAction execute(InputChanges inputChanges) {
                }
            }

            task noOutput(type: IncrementalTask) {
                input = file('${input.name}')
            }
        """

        when:
        fails 'noOutput'
        then:
        failure.assertHasDescription("Execution failed for task ':noOutput'.")
        failure.assertHasCause("You must declare outputs or use `TaskOutputs.upToDateWhen()` when using the incremental task API")
    }

    private static String declareSimpleCopyTask(boolean modification = false) {
        """
            ${declareSimpleCopyTaskType(modification)}

            task copy(type: SimpleCopy) {
                input = file("input.txt")
                output = file("output.txt")
            }
        """
    }

    private static String declareSimpleCopyTaskType(boolean modification = false) {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class SimpleCopy extends DefaultTask {
                @InputFile File input
                @OutputFile File output

                @TaskAction def action() {
                    output.text = input.text ${modification ? "+ 'modified'" : ""}
                }
            }
        """
    }

    private static String guavaDependency(String version) {
        """
            ${mavenCentralRepository()}
            dependencies {
                implementation "com.google.guava:guava:$version"
            }
        """
    }
}
