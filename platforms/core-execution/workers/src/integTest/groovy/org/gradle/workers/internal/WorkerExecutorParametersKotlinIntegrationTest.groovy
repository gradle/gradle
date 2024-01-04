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
import org.gradle.util.internal.ToBeImplemented
import org.gradle.workers.fixtures.WorkerExecutorFixture

class WorkerExecutorParametersKotlinIntegrationTest extends AbstractIntegrationSpec {

    @ToBeImplemented('https://github.com/gradle/gradle/issues/26596')
    def "can provide primitive #type array parameters with isolation mode #isolationMode"() {
        given:
        buildKotlinFile << """
            import org.gradle.workers.WorkAction
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkerExecutor

            interface TestParameters : WorkParameters {
                val array: Property<${type.toString().capitalize()}Array>
            }

            abstract class ParameterWorkAction : WorkAction<TestParameters> {
                override fun execute() {
                    val array = parameters.array.get()
                    println(array.javaClass.componentType.toString() + "ArrayOf" + array.joinToString(",", "(", ")"))
                }
            }

            abstract class ParameterTask : DefaultTask() {

                @get:Inject
                abstract val workerExecutor: WorkerExecutor

                @TaskAction
                fun doWork() {
                    workerExecutor.${isolationMode}().submit(ParameterWorkAction::class) {
                        array = ${type}ArrayOf(1, 2, 3)
                    }
                }
            }

            tasks {
                register<ParameterTask>("runWork") {
                }
            }
        """

        when:
        succeeds 'runWork'

        then:
        outputContains "${type}ArrayOf(1,2,3)"

        where:
        [isolationMode, type] << [
            WorkerExecutorFixture.IsolationMode.values(),
            ['byte', 'short', 'int', 'long']
        ].combinations()
    }
}
