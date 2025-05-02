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

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import java.util.concurrent.CompletableFuture

class GradleEnterprisePluginBackgroundJobExecutorsIntegrationTest extends AbstractDevelocityInputIgnoringServiceIntegrationTest {

    @Rule
    BlockingHttpServer httpServer = new BlockingHttpServer()

    @Override
    String runIgnoringInputs(String code) {
        """
            def executors = ${executors}
            def future = ${CompletableFuture.name}.runAsync({
                ${code}
            }, executors.userJobExecutor)

            // Block until the job completes to ensure it run at the configuration time.
            future.get()
        """
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

    static String getExecutors() {
        return """(gradle.extensions.serviceRef.get()._requiredServices.backgroundJobExecutors)"""
    }
}
