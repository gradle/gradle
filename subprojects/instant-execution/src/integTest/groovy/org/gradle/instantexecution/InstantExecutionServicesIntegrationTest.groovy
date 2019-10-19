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

import spock.lang.Ignore

class InstantExecutionServicesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    // TODO: logging from beforeTask and afterTask
    // TODO: failing from beforeTask
    // TODO: failing from afterTask
    // TODO: sharing state between beforeTask and afterTask using a WestlineService
    // TODO: afterAllTasks: set lava lamp color when tasks complete (red / green)
    @Ignore
    def "logging from beforeTask and afterTask"() {
        given:
        buildKotlinFile """

            import org.gradle.kotlin.dsl.support.*
            import kotlin.reflect.KClass
            import javax.inject.Inject
            
            interface WestlineEvents {
                fun <L : WestlineBeforeTaskListener<P>, P : WestlineListenerParameters> beforeTask(
                    listenerType: KClass<L>,
                    configuration: WestlineListenerSpec<P>.() -> Unit
                )
                fun <L : WestlineAfterTaskListener<P>, P : WestlineListenerParameters> afterTask(
                    listenerType: KClass<L>,
                    configuration: WestlineListenerSpec<P>.() -> Unit
                )
            }
            
            interface WestlineTaskInfo { val path: String }
            interface WestlineTaskExecutionResult { val outcome: String }
            interface WestlineListener<P : WestlineListenerParameters> {
                @get:Inject
                val parameters: P
            }
            interface WestlineListenerParameters
            interface WestlineListenerSpec<P : WestlineListenerParameters> {
                @get:Inject
                val parameters: P
            }
            interface WestlineBeforeTaskListener<P : WestlineListenerParameters> : WestlineListener<P> {
               fun beforeTask(taskInfo: WestlineTaskInfo) 
            }
            interface WestlineAfterTaskListener<P : WestlineListenerParameters> : WestlineListener<P> {
                fun afterTask(taskInfo: WestlineTaskInfo, taskExecutionResult: WestlineTaskExecutionResult)
            }
            
            interface MyBeforeTaskParameters : WestlineListenerParameters { val prompt: Property<String> }
            abstract class MyBeforeTaskListener : WestlineBeforeTaskListener<MyBeforeTaskParameters> {
                override fun beforeTask(taskInfo: WestlineTaskInfo) {
                    println(parameters.prompt.get() + " beforeTask " + taskInfo.path)
                }
            }
            
            interface MyAfterTaskParameters : WestlineListenerParameters { val prompt: Property<String> }
            abstract class MyAfterTaskListener : WestlineAfterTaskListener<MyAfterTaskParameters> {
                override fun afterTask(taskInfo: WestlineTaskInfo, taskExecutionResult: WestlineTaskExecutionResult) {
                    println(parameters.prompt.get() + " afterTask " + taskInfo.path + " " + taskExecutionResult.outcome)
                }
            }
            
            project.serviceOf<WestlineEvents>().apply {
                beforeTask(MyBeforeTaskListener::class) {
                    parameters.prompt.set("> ")
                }
                afterTask(MyAfterTaskListener::class) {
                    parameters.prompt.set("< ")
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

    def "westline services"() {

        given:
        buildKotlinFile """
            import java.util.concurrent.*
            import org.gradle.api.westline.*
            import org.gradle.kotlin.dsl.support.*

            val serviceFactory = project.serviceOf<WestlineServiceFactory>()

            val sysPropProvider = providers.systemProperty("thread.pool.size").map(Integer::valueOf)

            val threadPoolProvider = serviceFactory.createProviderOf(ThreadPoolService::class) {
                println("Configuring thread pool parameters...")
                parameters.threadPoolSize.set(sysPropProvider)
            }
    
            tasks {
    
                register<TaskA>("a") {
                    threadPool.set(threadPoolProvider)
                }
    
                register<TaskB>("b") {
                    threadPool.set(threadPoolProvider)
                }
            }

            abstract class TaskA : DefaultTask() {
            
                @get:Internal
                abstract val threadPool: Property<ThreadPoolService>
            
                @TaskAction
                fun act() {
                    println("About to use threadPool: ")
                    threadPool.get().execute("a") {
                        println("TaskA!")
                    }
                }
            }
            
            abstract class TaskB : DefaultTask() {
            
                @get:Internal
                abstract val threadPool: Property<ThreadPoolService>
            
                @TaskAction
                fun act() {
                    println("About to use threadPool: ")
                    threadPool.get().execute("b") {
                        println("TaskB!")
                    }
                }
            }

            interface ThreadPoolParameters : WestlineServiceParameters {
                val threadPoolSize: Property<Int>
            }

            abstract class ThreadPoolService : WestlineService<ThreadPoolParameters>, AutoCloseable {
            
                private
                val actions = CopyOnWriteArrayList<String>()
            
                init {
                    println("Creating thread pool with size ${'$'}{parameters.threadPoolSize.get()}")
                }
            
                fun execute(name: String, action: () -> Unit) {
                    actions.add(name)
                    println("Thread pool running action ${'$'}name...")
                    action()
                }
            
                override fun close() {
                    println("Closing thread pool after executing ${'$'}{actions.sorted()}.")
                }
            }

        """

        when:
        instantRun("a", "b", "-Dthread.pool.size=4")

        then:
        output.count("Configuring thread pool parameters...") == 1
        output.count("Creating thread pool with size 4") == 1

        output.indexOf("About to use threadPool: ") < output.indexOf("Creating thread pool with size 4")

        outputContains("Closing thread pool after executing [a, b].")

        when:
        instantRun("a", "b", "-Dthread.pool.size=4")

        then:
        result.assertNotOutput("Configuring thread pool parameters")
        output.count("Creating thread pool with size 4") == 1

        output.indexOf("About to use threadPool: ") < output.indexOf("Creating thread pool with size 4")

        outputContains("Closing thread pool after executing [a, b].")

        when:
        instantRun("a", "b", "-Dthread.pool.size=3")

        then:
        output.count("Configuring thread pool parameters...") == 1
        output.count("Creating thread pool with size 3") == 1

        output.indexOf("About to use threadPool: ") < output.indexOf("Creating thread pool with size 3")

        outputContains("Closing thread pool after executing [a, b].")
    }
}
