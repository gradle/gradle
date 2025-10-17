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

import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.TestPathExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractJvmFailFastIntegrationSpec
import org.gradle.testing.fixture.TestNGCoverage

@TargetCoverage({ [TestNGCoverage.NEWEST] })
class TestNGFailFastIntegrationTest extends AbstractJvmFailFastIntegrationSpec implements TestNGMultiVersionTest {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.TEST_NG
    }

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

        and:
        GenericTestExecutionResult testResults = resultsFor("tests/test", testFramework)
        assert 1 == resourceForTest.keySet().sum {path ->
            if (testResults.testPathExists(path)) {
                TestPathExecutionResult test = testResults.testPath(path)
                test.onlyRoot().getFailedChildCount()
            } else {
                0
            }
        }
        resourceForTest.keySet().with {
            def doesntExist = count {path ->
                !testResults.testPathExists(path)
            }
            def zeroChildren = count {path ->
                testResults.testPathExists(path) && testResults.testPath(path).rootNames.size() == 0
            }
            def skipped = count {path ->
                testResults.testPathExists(path) && testResults.testPath(path).onlyRoot().getSkippedChildCount()
            }
            assert 5 == (doesntExist + zeroChildren + skipped)
        }

        where:
        parallel  | threadCount | maxWorkers
        'methods' | 1           | 1
        'methods' | 2           | 1
        'methods' | 1           | 2
        'methods' | 2           | 2
        'classes' | 1           | 1
        'classes' | 2           | 1
        'classes' | 1           | 2
        'classes' | 2           | 2
    }
}
