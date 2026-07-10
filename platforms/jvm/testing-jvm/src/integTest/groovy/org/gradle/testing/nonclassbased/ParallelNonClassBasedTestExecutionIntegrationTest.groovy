/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.testing.nonclassbased

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.testing.ParallelTestExecutionIntegrationTest
import org.junit.Rule

/**
 * Tests that exercise and demonstrate parallel execution of Non-Class-Based Testing using the {@code Test} task
 * and a sample resource-based JUnit Platform Test Engine defined in this project's {@code testFixtures}.
 * <p>
 * This test uses a {@link BlockingHttpServer} to coordinate and verify parallel execution of tests.
 * See {@link ParallelTestExecutionIntegrationTest} for the basis of this approach.
 */
@IntegrationTestTimeout(300)
class ParallelNonClassBasedTestExecutionIntegrationTest extends AbstractNonClassBasedTestingIntegrationTest {
    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()


    @Override
    List<TestEngines> getEnginesToSetup() {
        return [TestEngines.BASIC_RESOURCE_BASED_PARALLEL]
    }

    def setup() {
        settingsFile << 'rootProject.name = "root"'
        blockingServer.start()
    }

    def "execute #maxConcurrency tests concurrently when maxWorkers=#maxWorkers and maxParallelForks=#maxParallelForks and forkEvery=#forkEvery"() {
        given:
        int testCount = maxConcurrency * 2
        println "Max-concurrency: $maxConcurrency"
        println "Test-count: $testCount"

        and:
        writeTestDefinitions(testCount)
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                ${enableEngineForSuite()}

                targets.all {
                    testTask.configure {
                        maxParallelForks = $maxParallelForks
                        forkEvery = $forkEvery

                        systemProperty("blocking.server.url", "${blockingServer.getUri().toString()}")

                        ${registerTestDefinitionDirs(testCount)}
                    }
                }
            }
        """

        and:
        executer.withArgument "--max-workers=$maxWorkers"

        and:
        def calls = testIndices(testCount).collect { "parallel-$it" } as String[]
        def handler = blockingServer.expectConcurrentAndBlock(maxConcurrency, calls)

        when:
        def gradle = executer.withTasks('test').start()

        then:
        handler.waitForAllPendingCalls()
        handler.release(1)

        and:
        if (maxConcurrency - 1 > 0) {
            handler.waitForAllPendingCalls()
            handler.release(maxConcurrency - 1)
        }

        and:
        handler.waitForAllPendingCalls()
        handler.release(maxConcurrency)

        then:
        gradle.waitForFinish()
        testIndices(testCount).each { idx ->
            resultsFor().assertAtLeastTestPathsExecuted(":parallel-${idx}.rbt - parallel-$idx")
        }

        where:
        maxConcurrency | maxWorkers | maxParallelForks | forkEvery
        1              | 1          | 1                | 0
        3              | 3          | 3                | 0
        2              | 2          | 3                | 0
        2              | 3          | 2                | 0
        1              | 1          | 1                | 1
        3              | 3          | 3                | 1
        2              | 2          | 3                | 1
        2              | 3          | 2                | 1
    }

    private void writeTestDefinitions(int testCount, String baseFileName = "parallel", String baseDefinitionsDir = "src/test/definitions") {
        testIndices(testCount).each { idx ->
            file("$baseDefinitionsDir-$idx/$baseFileName-${idx}.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
                <tests>
                    <test name="$baseFileName-$idx" />
                </tests>
            """
        }
    }

    private String registerTestDefinitionDirs(int testCount) {
        return testIndices(testCount).collect { idx ->
            "\t\t\ttestDefinitionDirs.from(\"src/test/definitions-$idx\")"
        }.join("\n")
    }

    private static int[] testIndices(int testCount) {
        (1..(testCount))
    }
}
