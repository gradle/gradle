/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest

@FluidDependenciesResolveTest
class DependencyManagementResultsAsInputsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
        buildFile << """
            abstract class TaskWithAttributeInput extends DefaultTask {

                @Input
                abstract Property<Attribute> getAttribute()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                def action() {
                    workerExecutor.processIsolation().submit(TaskWithAttributeInputWorkAction, parameters -> {
                        parameters.workAttribute.set(attribute)
                        parameters.workOutputFile.set(outputFile)
                    })
                }
            }

            interface TaskWithAttributeInputWorkParameters extends WorkParameters {
                Property<Attribute> getWorkAttribute()
                RegularFileProperty getWorkOutputFile()
            }

            abstract class TaskWithAttributeInputWorkAction implements WorkAction<TaskWithAttributeInputWorkParameters> {
                @Override
                void execute() {
                    println(parameters.workAttribute.get())
                }
            }
        """
    }

    def "attributes can be used as work inputs"() {
        given:
        buildFile << """
            tasks.register("verify", TaskWithAttributeInput) {
                outputFile.set(layout.buildDirectory.file('output.txt'))
                attribute.set(Attribute.of(System.getProperty("n"), String))
                doLast {
                    println(attribute.get())
                }
            }
        """

        when:
        succeeds("verify", "-Dn=foo")

        then:
        executedAndNotSkipped(":verify")

        when:
        succeeds("verify", "-Dn=foo")

        then:
        skipped(":verify")

        when:
        succeeds("verify", "-Dn=bar")

        then:
        executedAndNotSkipped(":verify")
    }
}
