/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.workers.fixtures.WorkerExecutorFixture
import org.gradle.workers.fixtures.WorkerExecutorFixture.IsolationMode
import spock.lang.Issue

class WorkerExecutorParametersKotlinIntegrationTest extends AbstractIntegrationSpec {

    @Issue('https://github.com/gradle/gradle/issues/26596')
    def "can provide primitive #type array parameter with #isolationMode isolation"() {
        given:
        withRunWorkTaskOf isolationMode, type

        when:
        succeeds 'runWork'

        then:
        outputContains expectedOutput

        when:
        succeeds 'runWork'

        then:
        outputDoesNotContain expectedOutput

        and:
        result.assertTaskSkipped ':runWork'

        where:
        [isolationMode, type] << [
            WorkerExecutorFixture.IsolationMode.values(),
            ['byte', 'short', 'int', 'long', 'float', 'double', 'char']
        ].combinations()
        expectedOutput = expectedOutputFor(type)
    }

    String expectedOutputFor(String type) {
        switch (type) {
            case 'char':
                return 'charArrayOf(*)' // 42.toChar() == '*'
            case 'float':
            case 'double':
                return "${type}ArrayOf(42.0)"
            default:
                return "${type}ArrayOf(42)"
        }
    }

    private TestFile withRunWorkTaskOf(IsolationMode isolationMode, String primitiveType) {
        def kotlinType = primitiveType.toString().capitalize()
        def kotlinArrayType = "${kotlinType}Array"
        buildKotlinFile << """
            import org.gradle.workers.WorkAction
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkerExecutor

            interface TestParameters : WorkParameters {
                @get:Input
                val array: Property<$kotlinArrayType>
            }

            abstract class ParameterWorkAction : WorkAction<TestParameters> {
                override fun execute() {
                    val array = parameters.array.get()
                    println(array.javaClass.componentType.toString() + "ArrayOf" + array.joinToString(",", "(", ")"))
                }
            }

            @CacheableTask
            abstract class ParameterTask : DefaultTask() {

                @get:Inject
                abstract val workerExecutor: WorkerExecutor

                @get:Input
                abstract val inputArray: Property<$kotlinArrayType>

                @get:OutputFile
                abstract val outputFile: RegularFileProperty

                @TaskAction
                fun doWork() {
                    workerExecutor.${isolationMode.method}().submit(ParameterWorkAction::class) {
                        array = inputArray
                    }
                    outputFile.get().asFile.writeText("done")
                }
            }

            tasks {
                register<ParameterTask>("runWork") {
                    inputArray = ${primitiveType}ArrayOf(42.to${kotlinType}())
                    outputFile = layout.buildDirectory.file("receipt")
                }
            }
        """
    }
}
