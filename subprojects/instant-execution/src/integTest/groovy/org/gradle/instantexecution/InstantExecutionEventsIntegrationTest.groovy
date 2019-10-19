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


// TODO experiment with event types instead of event functions
// TODO then play with reifying event types
class InstantExecutionEventsIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    // TODO: failing from beforeTask
    // TODO: failing from afterTask
    // TODO: afterAllTasks: set lava lamp color when tasks complete (red / green)
    // TODO how to annotate task instances and gather that in event handlers?

    def "gathering profiling data"() {

        // TODO add build option to conditionally enable profiling data gathering
        given:
        buildKotlinFile """
       
            import org.gradle.api.westline.*
            import org.gradle.api.westline.events.*
            import org.gradle.kotlin.dsl.support.*
             
            abstract class ProfilerService : WestlineService<ProfilerParameters>, AutoCloseable {
            
                fun taskStarted(signal: TaskStarted) {
                    println(signal)
                }
                
                fun taskEnded(signal: TaskEnded) {
                    println(signal)
                }
                
                override fun close() {
                    println("Saving profiling data to " + parameters.outputFile.get().asFile)
                }
            }
            
            data class TaskStarted(val path: String, val timestamp: Long)
            data class TaskEnded(val path: String, val timestamp: Long, val outcome: String)
            
            interface ProfilerParameters : WestlineServiceParameters {
                val outputFile: RegularFileProperty
            }
            
            val serviceFactory = project.serviceOf<WestlineServiceFactory>()
            val profilerProvider = serviceFactory.createProviderOf(ProfilerService::class) {
                parameters.outputFile.set(layout.buildDirectory.file("profiler-output.json"))
            }
  
            interface TaskProfilerParameters : WestlineListenerParameters {
                val profiler: Property<ProfilerService>
            }
            abstract class ProfilerBeforeTaskListener : WestlineBeforeTaskListener<TaskProfilerParameters> {
                override fun beforeTask(taskInfo: WestlineTaskInfo) {
                    parameters.profiler.get().taskStarted(
                        TaskStarted(taskInfo.path, 1L)
                    )
                }
            }
            abstract class ProfilerAfterTaskListener : WestlineAfterTaskListener<TaskProfilerParameters> {
                override fun afterTask(taskInfo: WestlineTaskInfo, taskResult: WestlineTaskExecutionResult) {
                    parameters.profiler.get().taskEnded(
                        TaskEnded(taskInfo.path, 2L, taskResult.outcome)
                    )
                }
            }
            
            project.serviceOf<WestlineEvents>().apply {
                beforeTask(ProfilerBeforeTaskListener::class) {
                    parameters.profiler.set(profilerProvider)
                }
                afterTask(ProfilerAfterTaskListener::class) {
                    parameters.profiler.set(profilerProvider)
                }
            } 
            
            tasks.register("task") {
                doLast { println("Action!") }
            }
        """

        when:
        instantRun "task"

        then:
        def started = "TaskStarted(path=:task, timestamp=1)"
        def action = "Action!"
        def ended = "TaskEnded(path=:task, timestamp=2, outcome=SUCCESS)"
        def saved = "Saving profiling data to"
        output.count(started) == 1
        output.count(action) == 1
        output.count(ended) == 1
        output.count(saved) == 1

        and:
        output.indexOf(started) < output.indexOf(action)
        output.indexOf(action) < output.indexOf(ended)
        output.indexOf(ended) < output.indexOf(saved)
    }

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
