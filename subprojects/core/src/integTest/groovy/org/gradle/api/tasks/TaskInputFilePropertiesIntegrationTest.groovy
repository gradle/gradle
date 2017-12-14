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
            class CustomTask extends DefaultTask {
                @Optional @$annotation.simpleName input
                @TaskAction void doSomething() {
                    assert inputs.files.empty
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
    def "TaskInputs.#method shows deprecation warning when used with complex input"() {
        buildFile << """
            task dependencyTask {
            }

            task test {
                inputs.$method(dependencyTask)
                doFirst {
                    // Need a task action to not skip this task
                }
            }
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds "test"

        output.contains "Using TaskInputs.$method() with something that doesn't resolve to a File object has been deprecated and is scheduled to be removed in Gradle 5.0. Use TaskInputs.files() instead."

        where:
        method << ["file", "dir"]
    }

    @Unroll
    def "TaskInputs.#method shows deprecation warning"() {
        buildFile << """
            task test {
                inputs.$method()
            }
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds "test"

        output.contains warning

        where:
        method              | warning
        "getHasInputs"      | "The TaskInputs.getHasInputs() method has been deprecated and is scheduled to be removed in Gradle 5.0. Declare individual task properties to access input files."
        "getHasSourceFiles" | "The TaskInputs.getHasSourceFiles() method has been deprecated and is scheduled to be removed in Gradle 5.0. Declare individual task properties to access source files."
        "getSourceFiles"    | "The TaskInputs.getSourceFiles() method has been deprecated and is scheduled to be removed in Gradle 5.0. Declare individual task properties to access source files."
    }

    @Unroll
    def "TaskOutputs.getHasOutput() shows deprecation warning"() {
        buildFile << """
            task test {
                outputs.hasOutput
            }
        """

        expect:
        executer.expectDeprecationWarning()
        succeeds "test"

        output.contains "The TaskOutputs.getHasOutput() method has been deprecated and is scheduled to be removed in Gradle 5.0. Declare individual task properties to access output files."
    }
}
