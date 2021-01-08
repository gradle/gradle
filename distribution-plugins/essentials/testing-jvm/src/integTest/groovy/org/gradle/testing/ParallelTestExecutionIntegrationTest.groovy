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
package org.gradle.testing

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.testing.fixture.JUnitCoverage.*

@IntegrationTestTimeout(240)
@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE })
class ParallelTestExecutionIntegrationTest extends JUnitMultiVersionIntegrationSpec {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        settingsFile << 'rootProject.name = "root"'
        buildFile << """
            plugins { id "java" }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation localGroovy()
                testImplementation "junit:junit:4.13"
            }
        """.stripIndent()

        blockingServer.start()
    }

    @Unroll
    def "execute #maxConcurrency tests concurrently when maxWorkers=#maxWorkers and maxParallelForks=#maxParallelForks and forkEvery=#forkEvery"() {
        given:
        int testCount = maxConcurrency * 2
        println "Max-concurrency: $maxConcurrency"
        println "Test-count: $testCount"

        and:
        withBlockingJUnitTests(testCount)
        buildFile << """
            test {
                maxParallelForks = $maxParallelForks
                forkEvery = $forkEvery
            }
        """.stripIndent()

        and:
        // Warm everything up ready to run the tests
        run("testClasses")

        and:
        executer.withArgument "--max-workers=$maxWorkers"

        and:
        def calls = testIndices(testCount).collect { "test_$it" } as String[]
        def handler = blockingServer.expectConcurrentAndBlock(maxConcurrency, calls)

        when:
        def gradle = executer.withArgument("-i").withTasks('test').start()

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

    def "can handle parallel tests together with parallel project execution"() {
        given:
        settingsFile << """
            include 'a'
            include 'b'
        """
        ["a", "b"].collect { file(it) }.each { TestFile build ->
            build.file("build.gradle") << """
                plugins { id "java" }
                ${mavenCentralRepository()}
                dependencies {
                    testImplementation localGroovy()
                    testImplementation "junit:junit:4.13"
                }
                test.maxParallelForks = 2
            """
            withNonBlockingJUnitTests(build, 200)
        }
        when:
        def gradle = executer.withArguments("--parallel", "--max-workers=2").withTasks('test').start()

        then:
        gradle.waitForFinish()
    }

    private void withBlockingJUnitTests(int testCount) {
        testIndices(testCount).each { idx ->
            file("src/test/java/pkg/SomeTest_${idx}.java") << """
                package pkg;
                import org.junit.Test;
                public class SomeTest_$idx {
                    @Test
                    public void test_$idx() {
                        ${blockingServer.callFromBuild("test_$idx")}
                    }
                }
            """.stripIndent()
        }
    }

    private void withNonBlockingJUnitTests(TestFile projectDir, int testCount) {
        testIndices(testCount).each { idx ->
            projectDir.file("src/test/java/pkg/SomeTest_${idx}.java") << """
                package pkg;
                import org.junit.Test;
                public class SomeTest_$idx {
                    @Test
                    public void test_$idx() {
                    }
                }
            """.stripIndent()
        }
    }

    private static int[] testIndices(int testCount) {
        (1..(testCount))
    }
}
