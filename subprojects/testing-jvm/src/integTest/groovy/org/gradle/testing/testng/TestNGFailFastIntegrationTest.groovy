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
import org.hamcrest.Matchers
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
        withBuildFile(maxWorkers)
        buildFile << """
            test {
                useTestNG() {
                    parallel = '$parallel'
                    threadCount = $threadCount
                }
            }
        """.stripIndent()
        withFailingTest()
        def otherResources = withNonfailingTests(5)
        def testExecution = server.expectMaybeAndBlock(maxWorkers * threadCount, ([ FAILED_RESOURCE ] + otherResources).grep() as String[])

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        sleep(1000)
        testExecution.releaseAll()
        gradleHandle.waitForFailure()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.FailedTest').assertTestFailed('failTest', Matchers.anything())
        if (result.getTotalNumberOfTestClassesExecuted() != 1) {
            result.testClassStartsWith("pkg.OtherTest").assertTestCount(0, 0, 0)
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
