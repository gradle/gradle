/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractJvmFailFastIntegrationSpec
import org.hamcrest.CoreMatchers
import spock.lang.Unroll

class TestNGFailFastIntegrationTest extends AbstractJvmFailFastIntegrationSpec {
    @Override
    String testAnnotationClass() {
        'org.testng.annotations.Test'
    }

    @Override
    String testDependency() {
        'org.testng:testng:6.9.13.6'
    }

    @Override
    String testFrameworkConfiguration() {
        """
            tasks.withType(Test) {
                useTestNG()
            }
        """
    }

    @Unroll
    def "parallel #parallel execution with #threadCount threads, #maxWorkers workers fails fast"() {
        given:
        buildFile.text = generator.initBuildFile(maxWorkers)
        buildFile << """
            test {
                useTestNG() {
                    parallel = '$parallel'
                    threadCount = $threadCount
                }
            }
        """.stripIndent()
        def resourceForTest = generator.withFailingTests(6)
        def testExecution = server.expectOptionalAndBlock(maxWorkers * threadCount, resourceForTest.values() as String[])

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(1)
        gradleHandle.waitForFailure()
        def result = new DefaultTestExecutionResult(testDirectory)
        assert 1 == resourceForTest.keySet().count { result.testClassExists(it) && result.testClass(it).testFailed('failedTest', CoreMatchers.anything()) }
        assert 5 == resourceForTest.keySet().with {
            count { !result.testClassExists(it) } +
                count { result.testClassExists(it) && result.testClass(it).testCount == 0 } +
                count { result.testClassExists(it) && result.testClass(it).testSkippedCount == 1 }
        }

        where:
        parallel    | threadCount | maxWorkers
        'methods'   | 1           | 1
        'methods'   | 2           | 1
        'methods'   | 1           | 2
        'methods'   | 2           | 2
        'classes'   | 1           | 1
        'classes'   | 2           | 1
        'classes'   | 1           | 2
        'classes'   | 2           | 2
    }
}
