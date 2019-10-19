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


class InstantExecutionEventsIntegrationTest extends AbstractInstantExecutionIntegrationTest  {

    // TODO: logging from beforeTask and afterTask
    // TODO: failing from beforeTask
    // TODO: failing from afterTask
    // TODO: sharing state between beforeTask and afterTask using a WestlineService
    // TODO: afterAllTasks: set lava lamp color when tasks complete (red / green)
    def "logging from beforeTask and afterTask"() {
        given:
        buildKotlinFile """
            import org.gradle.api.westline.events.*
            import org.gradle.kotlin.dsl.support.*
            
            interface MyBeforeTaskParameters : WestlineListenerParameters { val prompt: Property<String> }
            abstract class MyBeforeTaskListener : WestlineBeforeTaskListener<MyBeforeTaskParameters> {
                override fun beforeTask(taskInfo: WestlineTaskInfo) {
                    println(parameters.prompt.get() + " beforeTask " + taskInfo.path)
                }
            }
            
            interface MyAfterTaskParameters : WestlineListenerParameters { val prompt: Property<String> }
            abstract class MyAfterTaskListener : WestlineAfterTaskListener<MyAfterTaskParameters> {
                override fun afterTask(
                    taskInfo: WestlineTaskInfo, 
                    taskExecutionResult: WestlineTaskExecutionResult
                ) {
                    println(parameters.prompt.get() + " afterTask " + taskInfo.path + " " + taskExecutionResult.outcome)
                }
            }
            
            project.serviceOf<WestlineEvents>().apply {
                beforeTask(MyBeforeTaskListener::class) {
                    parameters.prompt.set(">")
                }
                afterTask(MyAfterTaskListener::class) {
                    parameters.prompt.set("<")
                }
            } 
            
            tasks.register("task") {
                doLast { println("Action!") }
            }
        """

        when:
        instantRun "task"

        then:
        output.count("> beforeTask :task") == 1
        output.count("Action!") == 1
        output.count("< afterTask :task SUCCESS") == 1

        and:
        output.indexOf("Action!") > output.indexOf("> beforeTask :task")
        output.indexOf("Action!") < output.indexOf("< afterTask :task")
    }

}
