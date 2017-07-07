/*
 * Copyright 2013 the original author or authors.
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
import spock.lang.Unroll

class FailingIncrementalTasksIntegrationTest extends AbstractIntegrationSpec {

    def "consecutively failing task has correct up-to-date status and failure"() {
        buildFile << """
            task foo {
                outputs.file("out.txt")
                doLast {
                    if (project.file("out.txt").exists()) {
                        throw new RuntimeException("Boo!")
                    }
                    project.file("out.txt") << "xxx"
                }
            }
        """

        expect:
        succeeds "foo"

        when:
        file("out.txt") << "force rerun"
        fails"foo"
        then:
        failureHasCause "Boo!"

        when:
        fails "foo", "--info"
        then:
        failureHasCause "Boo!"
        output.contains "Task has failed previously."
        //this exposes an issue we used to have with in-memory cache.
    }

    @Unroll
    def "incremental task after previous #failiureCount failure(s) #description"() {
        file("src/input.txt") << "input"
        buildFile << """
            class IncrementalTask extends DefaultTask {
                @InputDirectory File sourceDir
                @OutputDirectory File destinationDir
                
                @TaskAction
                void process(IncrementalTaskInputs inputs) {
                    project.file("\$destinationDir/output.txt").text = "output"
                    if (project.hasProperty("modifyOutputs")) {
                        switch (project.property("modifyOutputs")) {
                            case "add":
                                project.file("\$destinationDir/output-\${System.currentTimeMillis()}.txt").text = "output"
                                break
                            case "change":
                                project.file("\$destinationDir/output.txt").text = "changed output -- \${System.currentTimeMillis()}"
                                break
                            case "remove":
                                project.delete("\$destinationDir/output.txt")
                                break
                        }
                    }

                    if (project.hasProperty("fail")) {
                        throw new RuntimeException("Failure")
                    }
                    
                    if (project.hasProperty("expectIncremental")) {
                        def expectIncremental = Boolean.parseBoolean(project.property("expectIncremental"))
                        assert inputs.incremental == expectIncremental
                    }
                }
            }

            task incrementalTask(type: IncrementalTask) {
                sourceDir = file("src")
                destinationDir = file("build")
            }
        """

        succeeds "incrementalTask"

        file("src/input-change.txt") << "input"
        failiureCount.times {
            fails "incrementalTask", "-PmodifyOutputs=$modifyOutputs", "-Pfail"
        }

        expect:
        succeeds "incrementalTask", "-PexpectIncremental=$incremental"

        where:
        modifyOutputs | failiureCount | incremental | description
        "add"         | 1     | false       | "with additional outputs is fully rebuilt"
        "add"         | 2     | false       | "with additional outputs is fully rebuilt"
        "change"      | 1     | false       | "with changed outputs is fully rebuilt"
        "change"      | 2     | false       | "with changed outputs is fully rebuilt"
        "remove"      | 1     | false       | "with removed outputs is fully rebuilt"
        null          | 1     | true        | "with unmodified outputs is executed as incremental"
        null          | 2     | true        | "with unmodified outputs is executed as incremental"
    }
}
