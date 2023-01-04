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

class FailingIncrementalTasksIntegrationTest extends AbstractIntegrationSpec {

    def "consecutively failing task has correct up-to-date status and failure"() {
        buildFile << """
            task foo {
                def outFile = project.file("out.txt")
                outputs.file(outFile)
                doLast {
                    if (outFile.exists()) {
                        throw new RuntimeException("Boo!")
                    }
                    outFile << "xxx"
                }
            }
        """

        expect:
        succeeds "foo"

        when:
        file("out.txt") << "force rerun"
        fails "foo"
        then:
        failureHasCause "Boo!"

        when:
        fails "foo", "--info"
        then:
        failureHasCause "Boo!"
        output.contains "Task has failed previously."
        //this exposes an issue we used to have with in-memory cache.
    }

    def "incremental task after previous failure #description"() {
        file("src/input.txt") << "input"
        buildFile << """
            abstract class IncrementalTask extends DefaultTask {

                @Inject
                abstract ProviderFactory getProviders()

                @Inject
                abstract ProjectLayout getLayout()

                @Incremental @InputDirectory File sourceDir
                @OutputDirectory File destinationDir

                @TaskAction
                void process(InputChanges inputs) {
                    def outputTxt = layout.projectDirectory.file("\$destinationDir/output.txt").asFile
                    outputTxt.text = "output"
                    def modifyOutputs = providers.gradleProperty('modifyOutputs').orNull
                    if (modifyOutputs) {
                        switch (modifyOutputs) {
                            case "add":
                                layout.projectDirectory.file("\$destinationDir/output-\${System.currentTimeMillis()}.txt").asFile.text = "output"
                                break
                            case "change":
                                outputTxt.text = "changed output -- \${System.currentTimeMillis()}"
                                break
                            case "remove":
                                outputTxt.delete()
                                break
                        }
                    }

                    def expectIncremental = providers.gradleProperty('expectIncremental')
                    if (expectIncremental.isPresent()) {
                        assert inputs.incremental == expectIncremental.map { Boolean.parseBoolean(it) }.get()
                    }

                    if (providers.gradleProperty('fail').isPresent()) {
                        throw new RuntimeException("Failure")
                    }
                }
            }

            task incrementalTask(type: IncrementalTask) {
                sourceDir = file("src")
                destinationDir = file("build")
            }
        """
        succeeds "incrementalTask", "-PexpectIncremental=false"

        file("src/input-change.txt") << "input"
        fails "incrementalTask", "-PexpectIncremental=true", "-PmodifyOutputs=$modifyOutputs", "-Pfail"

        expect:
        succeeds "incrementalTask", "-PexpectIncremental=$incremental"

        where:
        modifyOutputs | incremental | description
        "add"         | false       | "with additional outputs is fully rebuilt"
        "change"      | false       | "with changed outputs is fully rebuilt"
        "remove"      | false       | "with removed outputs is fully rebuilt"
        "none"        | true        | "with unmodified outputs is executed as incremental"
    }
}
