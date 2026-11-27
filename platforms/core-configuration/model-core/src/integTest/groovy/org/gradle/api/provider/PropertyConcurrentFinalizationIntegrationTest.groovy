/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/29004")
class PropertyConcurrentFinalizationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
        createDirs("a", "b")
        settingsFile << "include 'a', 'b'"
    }

    @Requires(
        value = [TestExecutionPreconditions.NotConfigCached, TestExecutionPreconditions.NotParallelExecutor],
        reason = "the configuration cache gives each task its own copy of the property; --parallel is specified explicitly"
    )
    def "fails when tasks running in parallel finalize a shared property on read at the same time"() {
        buildFile """
            ${consumeTaskType()}

            def shared = objects.property(String)
            shared.set(providers.provider {
                ${server.callFromBuild("finalizing")}
                "value"
            })
            shared.finalizeValueOnRead()

            subprojects {
                tasks.register("consume", Consume) {
                    input.set(shared)
                }
            }
        """

        when:
        def failure = failWhileAnotherTaskIsFinalizing("consume")

        then:
        failure.assertHasDescription("Execution failed for task")
        failure.assertHasCause("Failed to calculate the value of task ")
        assertConcurrentFinalizationFailure(failure, "this property")
    }

    @Requires(value = TestExecutionPreconditions.IsConfigCached, reason = "the configuration cache gives each task its own copy of the property")
    def "tasks running in parallel each finalize their own copy of a shared property with the configuration cache"() {
        buildFile """
            ${consumeTaskType()}

            abstract class Produce extends DefaultTask {
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction def run() { outputFile.get().asFile.text = "produced" }
            }

            def produce = tasks.register("produce", Produce) {
                outputFile = layout.buildDirectory.file("produced.txt")
            }

            // The value comes from a task output, so the configuration cache keeps it lazy until execution
            def shared = objects.property(String)
            shared.set(produce.flatMap { it.outputFile }.map {
                ${server.callFromBuild("finalizing")}
                it.asFile.text
            })
            shared.finalizeValueOnRead()

            // The configuration cache runs tasks of the same project in parallel
            ["consumeA", "consumeB"].each {
                tasks.register(it, Consume) {
                    input.set(shared)
                }
            }
        """

        when:
        server.expectConcurrent("finalizing", "finalizing")
        run "consumeA", "consumeB", "--max-workers=2"

        then:
        outputContains(":consumeA: produced")
        outputContains(":consumeB: produced")

        when:
        server.expectConcurrent("finalizing", "finalizing")
        run "consumeA", "consumeB", "--max-workers=2"

        then:
        outputContains(":consumeA: produced")
        outputContains(":consumeB: produced")
    }

    @Requires(value = TestExecutionPreconditions.NotParallelExecutor, reason = "--parallel is specified explicitly")
    def "fails when tasks running in parallel finalize a property held by a shared build service at the same time"() {
        file("buildSrc/src/main/groovy/SharedState.groovy") << """
            import org.gradle.api.model.ObjectFactory
            import org.gradle.api.provider.Property
            import org.gradle.api.provider.ProviderFactory
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import javax.inject.Inject

            abstract class SharedState implements BuildService<BuildServiceParameters.None> {
                final Property<String> value

                @Inject
                SharedState(ObjectFactory objects, ProviderFactory providers) {
                    value = objects.property(String)
                    value.set(providers.provider {
                        ${server.callFromBuild("finalizing")}
                        "value"
                    })
                    value.finalizeValueOnRead()
                }
            }
        """
        file("buildSrc/src/main/groovy/ConsumeSharedState.groovy") << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.provider.Property
            import org.gradle.api.services.ServiceReference
            import org.gradle.api.tasks.TaskAction

            abstract class ConsumeSharedState extends DefaultTask {
                @ServiceReference("sharedState")
                abstract Property<SharedState> getSharedState()

                @TaskAction
                def run() {
                    println("\${path}: \${sharedState.get().value.get()}")
                }
            }
        """
        ["a", "b"].each {
            file("$it/build.gradle") << """
                gradle.sharedServices.registerIfAbsent("sharedState", SharedState)
                tasks.register("consume", ConsumeSharedState)
            """
        }

        when:
        def failure = failWhileAnotherTaskIsFinalizing("consume")

        then:
        failure.assertHasDescription("Execution failed for task")
        assertConcurrentFinalizationFailure(failure, "property 'value'")
    }

    private ExecutionFailure failWhileAnotherTaskIsFinalizing(String task) {
        def finalizing = server.expectAndBlock("finalizing")
        GradleHandle build = executer.withTasks(task).withArguments("--parallel", "--max-workers=2").start()
        try {
            finalizing.waitForAllPendingCalls()
            ConcurrentTestUtil.poll {
                assert build.standardOutput.contains("$task FAILED")
            }
        } finally {
            finalizing.releaseAll()
        }
        return build.waitForFailure()
    }

    private void assertConcurrentFinalizationFailure(ExecutionFailure failure, String displayName) {
        failure.assertHasCause(
            "Cannot finalize the value of $displayName because another thread is finalizing it. " +
                "Finalizing a value from multiple threads at once is not supported. " +
                documentationRegistry.getDocumentationRecommendationFor("information", "lazy_configuration", "sharing_properties_between_parallel_tasks")
        )
        failure.assertHasResolution("Do not share the same property or file collection between tasks that can run in parallel. Give each task its own property and connect it to the provider that supplies the value.")
        failure.assertHasResolution("If the value is available at configuration time, call 'finalizeValue()' on it before it is shared, for example when creating the build service that holds it.")
        failure.assertHasResolution("If tasks share the value through their inputs and the configuration cache is disabled, enable the configuration cache, so that each task works with its own copy of the value.")
    }

    private static String consumeTaskType() {
        """
            abstract class Consume extends DefaultTask {
                @Input abstract Property<String> getInput()
                @TaskAction def run() { println("\${path}: \${input.get()}") }
            }
        """
    }
}
