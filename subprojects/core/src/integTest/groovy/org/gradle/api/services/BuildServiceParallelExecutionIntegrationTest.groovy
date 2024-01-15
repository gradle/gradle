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
        settingsFile << """
            include 'a', 'b', 'c'
        """
        buildFile << """
            allprojects {
                task ping {
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

        buildFile << """
            def service = gradle.sharedServices.registerIfAbsent("exclusive", BuildService) {}

            allprojects {
                ping.usesService(service)
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

        buildFile << """
            def service = gradle.sharedServices.registerIfAbsent("exclusive", BuildService) {
                maxParallelUsages = 1
            }

            allprojects {
                ping.usesService(service)
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

        buildFile << """
            def service = gradle.sharedServices.registerIfAbsent("service", BuildService) {
                maxParallelUsages = 2
            }

            allprojects {
                ping.usesService(service)
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

        buildFile << """
            def service = gradle.sharedServices.registerIfAbsent("service", BuildService) {
                maxParallelUsages = 2
            }

            allprojects {
                ping.usesService(service)
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

        buildFile << """
            def service1 = gradle.sharedServices.registerIfAbsent("service1", BuildService) {
                maxParallelUsages = 2
            }
            def service2 = gradle.sharedServices.registerIfAbsent("service2", BuildService) {
                maxParallelUsages = 3
            }

            project(':a') {
                ping.usesService(service1)
                ping.usesService(service2)
            }
            project(':b') {
                ping.usesService(service1)
                ping.usesService(service2)
            }
            project(':c') {
                ping.usesService(service1)
                ping.usesService(service2)
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
