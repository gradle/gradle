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

package org.gradle.api.services

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.IgnoreWithInstantExecution
import org.gradle.integtests.fixtures.InstantExecutionRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.junit.runner.RunWith

@RunWith(InstantExecutionRunner)
class BuildServiceIntegrationTest extends AbstractIntegrationSpec {
    def "service is created once per build on first use and stopped at the end of the build"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }
            
            task first {
                doFirst {
                    provider.get().increment()
                }
            }

            task second {
                doFirst {
                    provider.get().increment()
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("help")

        then:
        result.assertNotOutput("service:")

        when:
        run("help")

        then:
        result.assertNotOutput("service:")
    }

    def "tasks can use mapped value of service"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }
            
            def count = provider.map { it.increment() + 10 }
            
            task first {
                doFirst {
                    println("got value = " + count.get())
                }
            }

            task second {
                doFirst {
                    println("got value = " + count.get())
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("got value = 21")
        outputContains("service: value is 12")
        outputContains("got value = 22")
        outputContains("service: closed with value 12")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("got value = 21")
        outputContains("service: value is 12")
        outputContains("got value = 22")
        outputContains("service: closed with value 12")
    }

    @RequiredFeatures(
        [@RequiredFeature(feature = "org.gradle.unsafe.instant-execution", value = "false")]
    )
    @IgnoreWithInstantExecution(IgnoreWithInstantExecution.Reason.UNSUPPORTED)
    def "service can be used at configuration and execution time"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }
            
            task count {
                doFirst {
                    provider.get().increment()
                }
            }

            provider.get().increment()
        """

        when:
        run("count")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("count")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("help")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: closed with value 11")
    }

    @RequiredFeatures(
        [@RequiredFeature(feature = "org.gradle.unsafe.instant-execution", value = "true")]
    )
    def "service used at configuration and execution time can be used with instant execution"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }
            
            task count {
                doFirst {
                    provider.get().increment()
                }
            }

            provider.get().increment()
        """

        when:
        run("count")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("count")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: closed with value 11")

        when:
        run("help")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: closed with value 11")

        when:
        run("help")

        then:
        result.assertNotOutput("service:")
    }

    def "plugin applied to multiple projects can register a shared service"() {
        settingsFile << "include 'a', 'b', 'c'"
        serviceImplementation()
        buildFile << """
            class CounterPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def provider = project.gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                        parameters.initial = 10
                    }
                    project.tasks.register("count") {
                        doFirst {
                            provider.get().increment()
                        }
                    }                
                }
            }
            subprojects {
                apply plugin: CounterPlugin
            }
        """

        when:
        run("count")

        then:
        output.count("service:") == 5
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: value is 13")
        outputContains("service: closed with value 13")

        when:
        run("count")

        then:
        output.count("service:") == 5
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: value is 13")
        outputContains("service: closed with value 13")
    }

    def "plugin can apply conventions to shared services of a given type"() {
        serviceImplementation()
        buildFile << """
            class CounterConventionPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.gradle.sharedServices.registrations.configureEach {
                        if (parameters instanceof Params) {
                            parameters.initial = parameters.initial.get() + 5
                        }
                    }
                }
            }
            apply plugin: CounterConventionPlugin

            def counter1 = project.gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 0
            }
            def counter2 = project.gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }
            task count {
                doLast {
                    counter1.get().increment()
                    counter2.get().increment()
                }
            }
        """

        when:
        run("count")

        then:
        output.count("service:") == 6
        outputContains("service: created with value = 5")
        outputContains("service: created with value = 15")
        outputContains("service: value is 6")
        outputContains("service: value is 16")
        outputContains("service: closed with value 6")
        outputContains("service: closed with value 16")

        when:
        run("count")

        then:
        output.count("service:") == 6
        outputContains("service: created with value = 5")
        outputContains("service: created with value = 15")
        outputContains("service: value is 6")
        outputContains("service: value is 16")
        outputContains("service: closed with value 6")
        outputContains("service: closed with value 16")
    }

    def "service parameters are isolated when the service is instantiated"() {
        serviceImplementation()
        buildFile << """
            def params
            
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                params = parameters
                parameters.initial = 10
            }
            
            assert params.initial.get() == 10
            params.initial = 12
            
            task first {
                doFirst {
                    params.initial = 15 // should have an effect
                    provider.get().reset()
                    params.initial = 1234 // should be ignored. Ideally should fail too
                }
            }

            task second {
                dependsOn first
                doFirst {
                    provider.get().increment()
                    params.initial = 456 // should be ignored
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 15")
        outputContains("service: value is 15")
        outputContains("service: value is 16")
        outputContains("service: closed with value 16")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 15")
        outputContains("service: value is 15")
        outputContains("service: value is 16")
        outputContains("service: closed with value 16")
    }

    def "service can take no parameters"() {
        noParametersServiceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {}
            
            task first {
                doFirst {
                    provider.get().increment()
                }
            }

            task second {
                doFirst {
                    provider.get().increment()
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: value is 2")

        when:
        run("first", "second")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: value is 2")
    }

    def "service is stopped even if build fails"() {
        serviceImplementation()
        buildFile << """
            def counter1 = project.gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 0
            }
            def counter2 = project.gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }
            task count {
                doLast {
                    counter1.get().increment()
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails("count")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: closed with value 1")

        when:
        fails("count")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: closed with value 1")
    }

    def "reports failure to create the service instance"() {
        brokenServiceImplementation()
        buildFile << """
            def provider1 = gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 10
            }
            def provider2 = gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }
            
            task first {
                doFirst {
                    provider1.get().increment()
                }
            }

            task second {
                doFirst {
                    provider2.get().increment()
                }
            }
        """

        when:
        fails("first", "second", "--continue")

        then:
        failure.assertHasFailures(2)
        failure.assertHasDescription("Execution failed for task ':first'.")
        failure.assertHasCause("Failed to create service 'counter1'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")
        failure.assertHasDescription("Execution failed for task ':second'.")
        failure.assertHasCause("Failed to create service 'counter2'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")

        when:
        fails("first", "second", "--continue")

        then:
        failure.assertHasFailures(2)
        failure.assertHasDescription("Execution failed for task ':first'.")
        failure.assertHasCause("Failed to create service 'counter1'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")
        failure.assertHasDescription("Execution failed for task ':second'.")
        failure.assertHasCause("Failed to create service 'counter2'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")
    }

    def "reports failure to stop the service instance"() {
        brokenStopServiceImplementation()
        buildFile << """
            def provider1 = gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 10
            }
            def provider2 = gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }
            
            task first {
                doFirst {
                    provider1.get().increment()
                }
            }

            task second {
                doFirst {
                    provider2.get().increment()
                }
            }
        """

        when:
        fails("first", "second")

        then:
        // TODO - improve the error handling so as to report both failures as top level failures
        // This documents existing behaviour, not desired behaviour
        failure.assertHasDescription("Failed to notify build listener")
        failure.assertHasCause("Failed to stop service 'counter1'.")
        failure.assertHasCause("broken")
        failure.assertHasCause("Failed to stop service 'counter2'.")
        failure.assertHasCause("broken")
    }

    def serviceImplementation() {
        buildFile << """
            interface Params extends BuildServiceParameters {
                Property<Integer> getInitial()
            }
            
            abstract class CountingService implements BuildService<Params>, AutoCloseable {
                int value
                
                CountingService() {
                    value = parameters.initial.get()
                    println("service: created with value = \${value}")
                }
                
                synchronized int getInitialValue() { return parameters.initial.get() }

                // Service must be thread-safe
                synchronized void reset() {
                    value = parameters.initial.get()
                    println("service: value is \${value}")
                }
                
                // Service must be thread-safe
                synchronized int increment() {
                    value++
                    println("service: value is \${value}")
                    return value
                }
                
                void close() {
                    println("service: closed with value \${value}")
                }
            } 
        """
    }

    def noParametersServiceImplementation() {
        buildFile << """
            abstract class CountingService implements BuildService<${BuildServiceParameters.name}.None> {
                int value
                
                CountingService() {
                    value = 0
                    println("service: created with value = \${value}")
                }
                
                // Service must be thread-safe
                synchronized int increment() {
                    value++
                    println("service: value is \${value}")
                    return value
                }
            } 
        """
    }

    def brokenServiceImplementation() {
        buildFile << """
            interface Params extends BuildServiceParameters {
                Property<Integer> getInitial()
            }
            
            abstract class CountingService implements BuildService<Params> {
                CountingService() {
                    throw new IOException("broken") // use a checked exception
                }
                
                void increment() {
                    throw new IOException("broken") // use a checked exception
                }
            } 
        """
    }

    def brokenStopServiceImplementation() {
        buildFile << """
            interface Params extends BuildServiceParameters {
                Property<Integer> getInitial()
            }
            
            abstract class CountingService implements BuildService<Params>, AutoCloseable {
                CountingService() {
                }
                
                void increment() {
                }
                
                void close() {
                    throw new IOException("broken") // use a checked exception
                }
            } 
        """
    }
}
