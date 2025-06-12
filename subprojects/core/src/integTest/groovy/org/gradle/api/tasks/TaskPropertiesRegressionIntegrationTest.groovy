/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

/**
 * Tests for previously existing mistakes in task properties.
 */
class TaskPropertiesRegressionIntegrationTest extends AbstractIntegrationSpec {
    @ToBeImplemented
    @Issue("https://github.com/gradle/gradle/issues/24747")
    def "nested property with final getter carries task dependencies in nested object"() {
        given:
        buildFile("""
            interface Parameters {
                @OutputFile
                RegularFileProperty getOutputFile()
            }

            abstract class ParameterizedTask<P> extends DefaultTask {
                private final P params

                protected ParameterizedTask(Class<P> paramType) {
                    this.params = objects.newInstance(paramType)
                }

                @Nested
                final P getParameters() {
                    return params
                }

                @Inject
                protected abstract ObjectFactory getObjects()
            }

            abstract class MyTask extends ParameterizedTask<Parameters> {
                @Inject
                MyTask() {
                    super(Parameters)
                }

                @TaskAction
                void doAction() {
                    parameters.outputFile.get().asFile.text = 'kthxbye'
                }
            }

            abstract class VerifyTask extends DefaultTask {
                @InputFile
                abstract RegularFileProperty getInputFile()
            }

            def task = tasks.register('myTask', MyTask) {
                parameters.outputFile = file('out.txt')
            }

            tasks.register('verify', VerifyTask) {
                inputFile = task.flatMap { it.parameters.outputFile }
            }
        """)

        when:
        fails("verify")

        then:
        failureCauseContains("Property 'outputFile' is declared as an output property of an object with type Parameters but does not have a task associated with it.")
    }
}
