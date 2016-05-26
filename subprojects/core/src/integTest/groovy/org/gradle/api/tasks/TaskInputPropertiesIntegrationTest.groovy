/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class TaskInputPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def "reports which properties are not serializable"() {
        buildFile << """
            task foo {
                inputs.property "a", "hello"
                inputs.property "b", new Foo()
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "" }
            }

            class Foo {
                int x
                String toString() { "xxx" }
            }
        """

        when: fails "foo"
        then: failure.assertHasCause("Unable to store task input properties. Property 'b' with value 'xxx")
    }

    def "deals gracefully with not serializable contents of GStrings"() {
        buildFile << """
            task foo {
                inputs.property "a", "hello \${new Foo()}"
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "" }
            }

            class Foo {
                int x
                String toString() { "xxx" }
            }
        """

        expect:
        run("foo").assertTaskNotSkipped(":foo")
        run("foo").assertTaskSkipped(":foo")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3435")
    @NotYetImplemented
    def "task is not up-to-date after file moved between properties"() {
        def taskDefinition = """
            class TaskWithTwoFileCollectionInputs extends DefaultTask {
                @InputFiles FileCollection inputs1
                @InputFiles FileCollection inputs2

                @OutputDirectory File output = project.buildDir

                @TaskAction void action() {}
            }
        """

        buildFile << """
            $taskDefinition

            task test(type: TaskWithTwoFileCollectionInputs) {
                inputs1 = files("input1.txt", "input2.txt")
                inputs2 = files("input3.txt")
            }
        """

        when:
        succeeds "test"

        then:
        skippedTasks.isEmpty()

        // Keep the same files, but move one of them to the other property
        buildFile.delete()
        buildFile << """
            $taskDefinition

            task test(type: TaskWithTwoFileCollectionInputs) {
                inputs1 = files("input1.txt")
                inputs2 = files("input2.txt", "input3.txt")
            }
        """

        when:
        succeeds "test"

        then:
        skippedTasks.isEmpty()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3435")
    @NotYetImplemented
    def "task is not up-to-date after swapping output directories between properties"() {
        def taskDefinition = """
            class TaskWithTwoOutputDirectoriesProperties extends DefaultTask {
                @InputFiles def inputFiles = project.files()

                @OutputDirectory File outputs1
                @OutputDirectory File outputs2

                @TaskAction void action() {}
            }
        """

        buildFile << """
            $taskDefinition

            task test(type: TaskWithTwoOutputDirectoriesProperties) {
                outputs1 = file("\$buildDir/output1")
                outputs2 = file("\$buildDir/output2")
            }
        """

        when:
        succeeds "test"

        then:
        skippedTasks.isEmpty()

        // Keep the same files, but move one of them to the other property
        buildFile.delete()
        buildFile << """
            $taskDefinition

            task test(type: TaskWithTwoOutputDirectoriesProperties) {
                outputs1 = file("\$buildDir/output2")
                outputs2 = file("\$buildDir/output1")
            }
        """

        when:
        succeeds "test"

        then:
        skippedTasks.isEmpty()
    }

    def "deprecation warning printed when @OutputFiles is used"() {
        buildFile << """
            class TaskWithOutputFilesProperty extends DefaultTask {
                @InputFiles def inputFiles = project.files()
                @OutputFiles Set<File> outputFiles = []
                @TaskAction void action() {}
            }

            task test(type: TaskWithOutputFilesProperty)
        """
        executer.expectDeprecationWarning()

        expect:
        succeeds "test"
        output.contains "The @OutputFiles annotation has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "Please use separate properties for each file annotated with @OutputFile, or reorganize output files under a single output directory annotated with @OutputDirectory."
    }

    def "deprecation warning printed when @OutputDirectories is used"() {
        buildFile << """
            class TaskWithOutputDirectoriesProperty extends DefaultTask {
                @InputFiles def inputFiles = project.files()
                @OutputDirectories Set<File> outputDirs = []
                @TaskAction void action() {}
            }

            task test(type: TaskWithOutputDirectoriesProperty) {
            }
        """
        executer.expectDeprecationWarning()

        expect:
        succeeds "test"
        output.contains "The @OutputDirectories annotation has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "Please use separate properties for each directory annotated with @OutputDirectory, or reorganize output under a single output directory."
    }

    def "deprecation warning printed when TaskOutputs.files() is used"() {
        buildFile << """
            task test {
                outputs.files("output.txt")
            }
        """
        executer.expectDeprecationWarning()

        expect:
        succeeds "test"
        output.contains "The TaskOutputs.files() method has been deprecated and is scheduled to be removed in Gradle 4.0. " +
            "Please use the TaskOutputs.file() or the TaskOutputs.dir() method instead."
    }
}
