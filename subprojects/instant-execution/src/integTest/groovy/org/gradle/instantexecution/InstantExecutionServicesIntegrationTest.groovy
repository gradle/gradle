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

class InstantExecutionServicesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "foo"() {

        given:
        buildKotlinFile.text = """
            import java.util.concurrent.*
            import org.gradle.api.westline.*
            import javax.inject.Inject
            import kotlin.reflect.KClass
            import org.gradle.kotlin.dsl.support.*

            val serviceFactory = project.serviceOf<WestlineServiceFactory>()

            val threadPoolProvider = serviceFactory.createProviderOf(ThreadPoolService::class) {
                println("Configuring thread pool parameters...")
                parameters.threadPoolSize.set(Integer.getInteger("thread.pool.size"))
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
    }
}
