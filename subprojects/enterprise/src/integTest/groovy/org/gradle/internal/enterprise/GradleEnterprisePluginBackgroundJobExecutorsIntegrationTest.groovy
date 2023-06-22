/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.process.ShellScript
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import org.junit.Rule

import javax.inject.Inject
import java.util.concurrent.CompletableFuture

class GradleEnterprisePluginBackgroundJobExecutorsIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer httpServer = new BlockingHttpServer()

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
    }

    def "background job executor runs jobs submitted at configuration time"() {
        given:
        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            CompletableFuture.runAsync({
                println "backgroundJobExecutor.executed"
            }, executors.userJobExecutor).exceptionally { e ->
                println "backgroundJobExecutor.status = \${e.cause.message}"
            }

            task check {}
        """

        when:
        succeeds("check")

        then:
        plugin.assertBackgroundJobCompletedBeforeShutdown(output, "backgroundJobExecutor.executed")
    }

    def "background job executor runs jobs submitted at execution time"() {
        given:
        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            task check {
                doLast {
                    CompletableFuture.runAsync({
                        println "backgroundJobExecutor.executed"
                    }, executors.userJobExecutor).exceptionally { e ->
                        println "backgroundJobExecutor.status = \${e.cause.message}"
                    }
                }
            }
        """

        when:
        succeeds("check")

        then:
        plugin.assertBackgroundJobCompletedBeforeShutdown(output, "backgroundJobExecutor.executed")

        when:
        succeeds("check")

        then:
        plugin.assertBackgroundJobCompletedBeforeShutdown(output, "backgroundJobExecutor.executed")
    }

    def "background job failure at configuration time fails build"() {
        given:
        buildFile << """
            def executors = $executors
            executors.userJobExecutor.execute {
                 throw new RuntimeException("Background job failed!")
            }

            task check {}
        """

        when:
        fails("check")

        then:
        failure.assertHasDescription("Background job failed!")
    }

    def "background job failure at execution time fails build"() {
        given:
        buildFile << """
            def executors = $executors

            task check {
                doLast {
                    executors.userJobExecutor.execute {
                         throw new RuntimeException("Background job failed!")
                    }
                }
            }
        """

        when:
        fails("check")

        then:
        failure.assertHasDescription("Background job failed!")

        when:
        fails("check")

        then:
        failure.assertHasDescription("Background job failed!")
    }

    def "background jobs can run in parallel with tasks on #numWorkers workers"(int numWorkers) {
        httpServer.start()

        given:
        buildFile << """
            def executors = $executors

            task check {
                doLast {
                    executors.userJobExecutor.execute {
                        ${httpServer.callFromBuild("background")}
                    }
                    ${httpServer.callFromBuild("task.action")}
                }
            }
        """

        when:
        httpServer.expectConcurrent("background", "task.action")

        then:
        succeeds("--max-workers=$numWorkers", "check")

        where:
        numWorkers << [1, 2]
    }

    def "background jobs can run in parallel with each other"() {
        httpServer.start()

        given:
        buildFile << """
            def executors = $executors

            executors.userJobExecutor.execute {
                ${httpServer.callFromBuild("background1")}
            }
            executors.userJobExecutor.execute {
                ${httpServer.callFromBuild("background2")}
            }

            task check {}
        """

        when:
        httpServer.expectConcurrent("background1", "background2")

        then:
        succeeds("check")
    }


    def "jobs can query if they are running on the background executor"() {
        given:
        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            CompletableFuture.runAsync({
                println "backgroundJobExecutor.isInBackground = \${executors.isInBackground()}"
            }, executors.userJobExecutor).exceptionally { e ->
                println "backgroundJobExecutor.status = \${e.cause.message}"
            }

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.isInBackground = true")
    }

    def "buildscript execution is not on the background executor"() {
        given:
        buildFile << """
            def executors = $executors
            println "backgroundJobExecutor.isInBackground = \${executors.isInBackground()}"

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.isInBackground = false")
    }

    def "task execution is not on the background executor"() {
        given:
        buildFile << """
            def executors = $executors

            task check {
                doLast {
                    println "backgroundJobExecutor.isInBackground = \${executors.isInBackground()}"
                }
            }
        """

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.isInBackground = false")

        when:
        succeeds("check")

        then:
        outputContains("backgroundJobExecutor.isInBackground = false")
    }

    @Requires(IntegTestPreconditions.IsConfigCached)
    def "configuration inputs are not tracked for the job"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        given:
        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            def future = CompletableFuture.runAsync({
                println "backgroundJob.property = \${System.getProperty("property")}"
            }, executors.userJobExecutor)

            // Block until the job completes to ensure it run at the configuration time.
            future.get()

            task check {}
        """

        when:
        succeeds("check", "-Dproperty=value")

        then:
        outputContains("backgroundJob.property = value")
        configurationCache.assertStateStored()

        when:
        succeeds("check", "-Dproperty=other")

        then:
        configurationCache.assertStateLoaded()
    }

    @Requires(IntegTestPreconditions.IsConfigCached)
    @ToBeImplemented("https://github.com/gradle/gradle/issues/25474")
    def "value sources are not tracked for the job"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        given:
        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            def propertyProvider = providers.systemProperty("property")
            def future = CompletableFuture.runAsync({
                println "backgroundJob.property = \${propertyProvider.get()}"
            }, executors.userJobExecutor)

            // Block until the job completes to ensure it run at the configuration time.
            future.get()

            task check {}
        """

        when:
        succeeds("check", "-Dproperty=value")

        then:
        outputContains("backgroundJob.property = value")
        configurationCache.assertStateStored()

        when:
        succeeds("check", "-Dproperty=other")

        then:
        // TODO(mlopatkin) Accessing the value source in the background job should not make it an input,
        //  so the configuration should be loaded from the cache. A naive solution of gating the input
        //  recording will break the other test as only the first value source read is broadcasted to
        //  listeners.
        configurationCache.assertStateStored() // TODO: replace with .assertStateLoaded() once the above is implemented
        outputContains("backgroundJob.property = other")
    }

    @Requires(IntegTestPreconditions.IsConfigCached)
    def "value sources are tracked if also accessed outside the job"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        given:
        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            def propertyProvider = providers.systemProperty("property")
            def future = CompletableFuture.runAsync({
                println "backgroundJob.property = \${propertyProvider.get()}"
            }, executors.userJobExecutor)

            // Block until the job completes to ensure it run at the configuration time.
            future.get()

            println "buildscript.property = \${propertyProvider.get()}"

            task check {}
        """

        when:
        succeeds("check", "-Dproperty=value")

        then:
        outputContains("backgroundJob.property = value")
        outputContains("buildscript.property = value")
        configurationCache.assertStateStored()

        when:
        succeeds("check", "-Dproperty=other")

        then:
        outputContains("backgroundJob.property = other")
        outputContains("buildscript.property = other")
        configurationCache.assertStateStored()
    }

    def "background job can execute external process with process API at configuration time"() {
        given:
        ShellScript script = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile << """
            import ${CompletableFuture.name}

            def executors = $executors
            def future = CompletableFuture.runAsync({
                def process = ${ShellScript.cmdToStringLiteral(script.getRelativeCommandLine(testDirectory))}.execute()
                process.waitForProcessOutput(System.out, System.err)
            }, executors.userJobExecutor)

            // Block until the job completes to ensure it run at the configuration time.
            future.get()

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("Hello, world")
    }

    def "background job can execute external process with Gradle API at configuration time"() {
        given:
        ShellScript script = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile << """
            import ${CompletableFuture.name}
            import ${Inject.name}

            interface ExecOperationsGetter {
                @Inject ExecOperations getExecOps()
            }

            def execOperations = objects.newInstance(ExecOperationsGetter).execOps

            def executors = $executors
            def future = CompletableFuture.runAsync({
                execOperations.exec {
                    commandLine(${ShellScript.cmdToVarargLiterals(script.getRelativeCommandLine(testDirectory))})
                }
            }, executors.userJobExecutor)

            // Block until the job completes to ensure it run at the configuration time.
            future.get()

            task check {}
        """

        when:
        succeeds("check")

        then:
        outputContains("Hello, world")
    }

    private static String getExecutors() {
        return """(gradle.extensions.serviceRef.get()._requiredServices.backgroundJobExecutors)"""
    }
}
