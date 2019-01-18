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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class TaskInputFilePropertiesIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "allows optional @#annotation.simpleName to have null value"() {
        buildFile << """
            import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
            import org.gradle.api.internal.tasks.TaskPropertyUtils
            import org.gradle.api.internal.tasks.properties.PropertyWalker
            import org.gradle.api.internal.file.FileResolver

            class CustomTask extends DefaultTask {
                @Optional @$annotation.simpleName input
                @TaskAction void doSomething() {
                    def fileResolver = project.services.get(FileResolver)
                    GetInputFilesVisitor visitor = new GetInputFilesVisitor("ownerName", fileResolver)
                    def walker = services.get(PropertyWalker)
                    TaskPropertyUtils.visitProperties(walker, this, visitor)
                    def inputFiles = visitor.fileProperties*.propertyFiles*.files.flatten()
                    assert inputFiles.empty
                }
            }

            task customTask(type: CustomTask) {
                input = null
            }
        """

        expect:
        succeeds "customTask"

        where:
        annotation << [ InputFile, InputDirectory, InputFiles ]
    }

    @Unroll
    @Issue("https://github.com/gradle/gradle/issues/3193")
    def "TaskInputs.#method shows error message when used with complex input"() {
        buildFile << """
            task dependencyTask {
            }

            task test {
                inputs.$method(dependencyTask).withPropertyName('input')
                doFirst {
                    // Need a task action to not skip this task
                }
            }
        """

        expect:
        fails "test"
        failure.assertHasDescription("A problem was found with the configuration of task ':test'.")
        failure.assertHasCause("Value 'task ':dependencyTask'' specified for property 'input' cannot be converted to a ${targetType}.")

        where:
        method | targetType
        "dir"  | "directory"
        "file" | "file"
    }

    @Unroll
    def "#annotation.simpleName shows error message when used with complex input"() {
        buildFile << """
            import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
            import org.gradle.api.internal.tasks.TaskPropertyUtils
            import org.gradle.api.internal.tasks.properties.PropertyWalker

            class CustomTask extends DefaultTask {
                @Optional @${annotation.name} input
                @TaskAction void doSomething() {
                    println("Yay!")
                }
            }

            task dependencyTask {
            }

            task customTask(type: CustomTask) {
                input = dependencyTask
            }
        """

        expect:
        fails "customTask"
        failure.assertHasDescription("A problem was found with the configuration of task ':customTask'.")
        failure.assertHasCause("Value 'task ':dependencyTask'' specified for property 'input' cannot be converted to a ${targetType}.")

        where:
        annotation     | targetType
        InputDirectory | "directory"
        InputFile      | "file"
    }

    @Issue("https://github.com/gradle/gradle/issues/3792")
    def "task dependency is discovered via Buildable input files"() {
        buildFile << """
            @groovy.transform.TupleConstructor
            class BuildableArtifact implements Buildable, Iterable<File> {
                FileCollection files

                Iterator<File> iterator() {
                    files.iterator()
                }

                TaskDependency getBuildDependencies() {
                    files.getBuildDependencies()
                }
            }

            task foo {
                outputs.file "foo.txt"
                doFirst {}
            }

            task bar {
                inputs.files(new BuildableArtifact(files(foo)))
                outputs.file "bar.txt"
                doFirst {}
            }
        """

        when:
        run "bar"
        then:
        executed ":foo"
    }
}
