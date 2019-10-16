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

package org.gradle.instantexecution

import org.intellij.lang.annotations.Language

class InstantExecutionBuildOptionsIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "build options used by tasks"() {

        given:
        def instant = newInstantExecutionFixture()
        @Language("kotlin") def script = """

            val sysPropProvider = providers.systemProperty("thread.pool.size").map(Integer::valueOf)
            
            abstract class TaskA : DefaultTask() {
            
                @get:Internal
                abstract val threadPoolSize: Property<Int>
            
                @TaskAction
                fun act() {
                    println("ThreadPoolSize = "+ threadPoolSize.get())
                }
            }
            
            tasks.register<TaskA>("a") {
                threadPoolSize.set(sysPropProvider)
            }
        """
        buildKotlinFile << script

        when:
        instantRun("a", "-Dthread.pool.size=4")

        then:
        output.count("ThreadPoolSize = 4") == 1
        instant.assertStateStored()

        when:
        instantRun("a", "-Dthread.pool.size=3")

        then:
        output.count("ThreadPoolSize = 3") == 1
        instant.assertStateLoaded()
    }
}
