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

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.tasks.TaskPropertyUtils
import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import spock.lang.Issue

class TaskInputFilePropertiesIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {
    def setup() {
        expectReindentedValidationMessage()
    }

    def "allows optional @#annotation.simpleName to have null value"() {
        buildFile << """
            import ${GetInputFilesVisitor.name}
            import ${TaskPropertyUtils.name}
            import ${PropertyWalker.name}
            import ${FileCollectionFactory.name}

            class CustomTask extends DefaultTask {
                @Optional @$annotation.simpleName input
                @TaskAction void doSomething() {
                    def fileCollectionFactory = services.get(FileCollectionFactory)
                    GetInputFilesVisitor visitor = new GetInputFilesVisitor("ownerName", fileCollectionFactory)
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
        annotation << [InputFile, InputDirectory, InputFiles]
    }

    @ValidationTestFor(
        ValidationProblemId.UNSUPPORTED_NOTATION
    )
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
        failure.assertHasDescription("A problem was found with the configuration of task ':test' (type 'DefaultTask').")
        failureDescriptionContains(unsupportedNotation {
            property('input')
                .value("task ':dependencyTask'")
                .cannotBeConvertedTo(targetType)
                .candidates(
                    "a String or CharSequence path, for example 'src/main/java' or '/usr/include'",
                    "a String or CharSequence URI, for example 'file:/usr/include'",
                    "a File instance",
                    "a Path instance",
                    "a Directory instance",
                    "a RegularFile instance",
                    "a URI or URL instance",
                    "a TextResource instance"
                ).includeLink()
        })

        where:
        method | targetType
        "dir"  | "directory"
        "file" | "file"
    }

    @ValidationTestFor(
        ValidationProblemId.UNSUPPORTED_NOTATION
    )
    @ToBeFixedForConfigurationCache(because = "multiple build failures")
    def "#annotation.simpleName shows error message when used with complex input"() {
        buildFile << """
            import org.gradle.api.internal.tasks.properties.GetInputFilesVisitor
            import org.gradle.api.internal.tasks.TaskPropertyUtils
            import org.gradle.internal.properties.bean.PropertyWalker

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
        failure.assertHasDescription("A problem was found with the configuration of task ':customTask' (type 'CustomTask').")
        failureDescriptionContains(unsupportedNotation {
            type('CustomTask').property('input')
                .value("task ':dependencyTask'")
                .cannotBeConvertedTo(targetType)
                .candidates(
                    "a String or CharSequence path, for example 'src/main/java' or '/usr/include'",
                    "a String or CharSequence URI, for example 'file:/usr/include'",
                    "a File instance",
                    "a Path instance",
                    "a Directory instance",
                    "a RegularFile instance",
                    "a URI or URL instance",
                    "a TextResource instance"
                ).includeLink()
        })

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

    @Issue("https://github.com/gradle/gradle/issues/9674")
    def "allows @InputFiles of task with no actions to be null"() {
        buildFile << """
            class FooTask extends DefaultTask {
               @InputFiles
               FileCollection bar
            }

            task foo(type: FooTask)
        """

        when:
        run "foo"

        then:
        executed ":foo"
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    @Issue("https://github.com/gradle/gradle/issues/9674")
    def "shows validation error when non-Optional @Input is null"() {
        buildFile << """
            class FooTask extends DefaultTask {
               @InputFiles
               FileCollection bar

               @TaskAction
               def go() {
               }
            }

            task foo(type: FooTask)
        """

        when:
        fails "foo"

        then:
        failureDescriptionContains(missingValueMessage { type('FooTask').property('bar') })
    }
}
