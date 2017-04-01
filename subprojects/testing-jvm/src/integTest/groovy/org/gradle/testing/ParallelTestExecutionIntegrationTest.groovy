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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll

@Timeout(60)
@IgnoreIf({ GradleContextualExecuter.isParallel() })
class ParallelTestExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        settingsFile << 'rootProject.name = "root"'
        buildFile << """
            plugins { id "groovy" }
            repositories { jcenter() }
            dependencies {
                testCompile localGroovy()
                testCompile "junit:junit:4.12"
            }
        """.stripIndent()

        blockingServer.start()

        executer.withArgument '--parallel'
    }

    def cleanup() {
        blockingServer.stop()
    }

    @Unroll
    def "execute #maxConcurrency tests concurrently when maxWorkers=#maxWorkers and maxParallelForks=#maxParallelForks and forkEvery=#forkEvery"() {
        given:
        int testCount = maxConcurrency * 2
        println "Max-concurrency: $maxConcurrency"
        println "Test-count: $testCount"

        and:
        withJUnitTests(testCount)
        buildFile << """
            test {
                maxParallelForks = $maxParallelForks
                forkEvery = $forkEvery
            }
        """.stripIndent()

        and:
        executer.withArgument "--max-workers=$maxWorkers"

        and:
        def calls = testIndices(testCount).collect { "test_$it" } as String[]
        def handler = blockingServer.blockOnConcurrentExecutionAnyOf(maxConcurrency, calls)

        when:
        def gradle = executer.withTasks('test').start()

        then:
        handler.waitForAllPendingCalls(30)
        handler.release(1)

        and:
        if (maxConcurrency - 1 > 0) {
            handler.waitForAllPendingCalls(30)
            handler.release(maxConcurrency - 1)
        }

        and:
        handler.waitForAllPendingCalls(30)
        handler.release(maxConcurrency)

        then:
        gradle.waitForFinish()

        where:
        maxConcurrency | maxWorkers | maxParallelForks | forkEvery
        1              | 1          | 1                | 0
        3              | 3          | 3                | 0
        2              | 2          | 3                | 0
        2              | 3          | 2                | 0
        1              | 1          | 1                | 2
        3              | 3          | 3                | 2
        2              | 2          | 3                | 2
        2              | 3          | 2                | 2
    }

    private void withJUnitTests(int testCount) {
        testIndices(testCount).each { idx ->
            file("src/test/groovy/${pkg(idx)}/SomeTest_${idx}.groovy") << """
                package ${pkg(idx)}
                import org.junit.Test
                public class SomeTest_$idx {
                    @Test
                    void test_$idx() {
                        URL url = new URL("${blockingServer.uri("test_$idx")}")
                        println url.openConnection().getHeaderField('RESPONSE')                        
                    }
                }
            """.stripIndent()
        }
    }

    private static int[] testIndices(int testCount) {
        (1..(testCount))
    }

    private static String pkg(int testIndex) {
        "pkg${testIndex % 100}"
    }
}
