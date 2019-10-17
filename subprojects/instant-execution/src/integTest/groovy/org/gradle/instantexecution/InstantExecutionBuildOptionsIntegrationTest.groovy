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
import spock.lang.Unroll

class InstantExecutionBuildOptionsIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "system property used as task input"() {

        given:
        def instant = newInstantExecutionFixture()
        buildKotlinFile("""

            val sysPropProvider = providers.systemProperty("thread.pool.size").map(Integer::valueOf)
            
            abstract class TaskA : DefaultTask() {

                @get:Input
                abstract val threadPoolSize: Property<Int>
            
                @TaskAction
                fun act() {
                    println("ThreadPoolSize = "+ threadPoolSize.get())
                }
            }
            
            tasks.register<TaskA>("a") {
                threadPoolSize.set(sysPropProvider)
            }
        """)

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

    @Unroll
    def "system property #usage used as build logic input"() {

        given:
        def instant = newInstantExecutionFixture()
        buildKotlinFile """
            val isCi = providers.systemProperty("ci")
            if ($expression) {
                tasks.register("run") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("run") {
                    doLast { println("NOT CI") }
                }            
            }
        """

        when:
        instantRun "run"

        then:
        output.count("NOT CI") == 1
        instant.assertStateStored()

        when:
        instantRun "run"

        then:
        output.count("NOT CI") == 1
        instant.assertStateLoaded()

        when:
        instantRun "run", "-Dci=true"

        then:
        output.count("ON CI") == 1
        instant.assertStateStored()

        where:
        expression                                     | usage
        "isCi.map(String::toBoolean).getOrElse(false)" | "value"
        "isCi.isPresent"                               | "presence"
    }

    void buildKotlinFile(@Language("kotlin") String script) {
        buildKotlinFile << script
    }
}
