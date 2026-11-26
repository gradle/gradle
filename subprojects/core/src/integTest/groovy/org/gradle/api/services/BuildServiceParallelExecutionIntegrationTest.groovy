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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class BuildServiceParallelExecutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        createDirs("a", "b", "c")
        settingsFile """
            include 'a', 'b', 'c'

            abstract class EmptyService implements BuildService<org.gradle.api.services.BuildServiceParameters.None> {}

            gradle.lifecycle.beforeProject { project ->
                project.tasks.register("ping") {
                    def projectName = project.name
                    doLast {
                        ${blockingServer.callFromBuildUsingExpression("projectName")}
                    }
                }
            }
        """
    }

    def "tasks run in parallel when no max usages specified"() {
        given:
        withParallelThreads(2)

        settingsFile """
            gradle.lifecycle.beforeProject { project ->
                def service = project.gradle.sharedServices.registerIfAbsent("exclusive", EmptyService) {}
                project.tasks.named("ping") {
                    usesService(service)
                }
            }
        """

        expect:
        blockingServer.expectConcurrent("a", "b")

        run ":a:ping", ":b:ping"

        and:
        blockingServer.expectConcurrent("a", "b")

        run ":a:ping", ":b:ping"
    }

    def "tasks are not run in parallel when they require an exclusive shared service"() {
        given:
        withParallelThreads(2)

        settingsFile """
            gradle.lifecycle.beforeProject { project ->
                def service = project.gradle.sharedServices.registerIfAbsent("exclusive", EmptyService) {
                    maxParallelUsages = 1
                }
                project.tasks.named("ping") {
                    usesService(service)
                }
            }
        """

        expect:
        blockingServer.expectConcurrent(1, "a", "b")

        run ":a:ping", ":b:ping"

        blockingServer.expectConcurrent(1, "a", "b")

        run ":a:ping", ":b:ping"
    }

    def "tasks run in parallel when sufficient max usages"() {
        given:
        withParallelThreads(2)

        settingsFile """
            gradle.lifecycle.beforeProject { project ->
                def service = project.gradle.sharedServices.registerIfAbsent("service", EmptyService) {
                    maxParallelUsages = 2
                }
                project.tasks.named("ping") {
                    usesService(service)
                }
            }
        """

        expect:
        blockingServer.expectConcurrent("a", "b")

        run ":a:ping", ":b:ping"

        blockingServer.expectConcurrent("a", "b")

        run ":a:ping", ":b:ping"
    }

    def "task parallelization is limited by max usages"() {
        given:
        withParallelThreads(3)

        settingsFile """
            gradle.lifecycle.beforeProject { project ->
                def service = project.gradle.sharedServices.registerIfAbsent("service", EmptyService) {
                    maxParallelUsages = 2
                }
                project.tasks.named("ping") {
                    usesService(service)
                }
            }
        """

        expect:
        blockingServer.expectConcurrent(2, "a", "b", "c")

        run ":a:ping", ":b:ping", ":c:ping"

        blockingServer.expectConcurrent(2, "a", "b", "c")

        run ":a:ping", ":b:ping", ":c:ping"
    }

    def "task can use multiple services"() {
        given:
        withParallelThreads(3)

        settingsFile """
            gradle.lifecycle.beforeProject { project ->
                def service1 = project.gradle.sharedServices.registerIfAbsent("service1", EmptyService) {
                    maxParallelUsages = 2
                }
                def service2 = project.gradle.sharedServices.registerIfAbsent("service2", EmptyService) {
                    maxParallelUsages = 3
                }
                project.tasks.named("ping") {
                    usesService(service1)
                    usesService(service2)
                }
            }
        """

        expect:
        blockingServer.expectConcurrent(2, "a", "b", "c")

        run ":a:ping", ":b:ping", ":c:ping"

        blockingServer.expectConcurrent(2, "a", "b", "c")

        run ":a:ping", ":b:ping", ":c:ping"
    }

    void withParallelThreads(int threadCount) {
        executer.beforeExecute {
            withArguments("--max-workers=$threadCount", "--parallel")
        }
    }
}
