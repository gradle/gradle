/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.tasks.TasksWithInputsAndOutputs

import javax.inject.Inject

class ConfigurationCacheWorkerApiIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements TasksWithInputsAndOutputs {
    def "task can wire input #type with fixed value to worker action parameter property"() {
        buildFile << """
            import ${Inject.name}

            abstract class UsesWorker extends DefaultTask {
                @Input
                abstract ${type} getValue()

                @Inject
                abstract WorkerExecutor getExecutor()

                @TaskAction
                def go() {
                    def flag = value
                    executor.noIsolation().submit(SomeWorkAction) {
                        value = flag
                    }
                }
            }

            interface SomeParams extends WorkParameters {
                ${type} getValue()
            }

            abstract class SomeWorkAction implements WorkAction<SomeParams> {
                void execute() {
                    println("value = \${parameters.value.get()}")
                }
            }

            task worker(type: UsesWorker) {
                value = ${initialValue}
            }
        """

        when:
        configurationCacheRun("worker")
        configurationCacheRun("worker")

        then:
        outputContains("value = ${expectedOutput}")

        where:
        type                           | initialValue   | expectedOutput
        "Property<Boolean>"            | "true"         | "true"
        "ListProperty<Integer>"        | "[1, 2]"       | "[1, 2]"
        "SetProperty<Integer>"         | "[1, 2, 1]"    | "[1, 2]"
        "MapProperty<String, Integer>" | "[a: 1, b: 2]" | "[a:1, b:2]"
    }
}
