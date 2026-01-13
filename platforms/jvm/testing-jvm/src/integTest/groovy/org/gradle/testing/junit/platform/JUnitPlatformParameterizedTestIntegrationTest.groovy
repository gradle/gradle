/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.platform

import org.gradle.api.tasks.testing.TestResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.testing.fixture.JUnitPlatformTestFixture
import org.hamcrest.CoreMatchers
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUPITER_VERSION
import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_PLATFORM_VERSION


class JUnitPlatformParameterizedTestIntegrationTest extends JUnitPlatformIntegrationSpec implements JUnitPlatformTestFixture {
    @Override
    String getJupiterVersion() {
        return LATEST_JUPITER_VERSION
    }

    @Override
    TestFile getProjectDir() {
        return testDirectory
    }

    @Rule BlockingHttpServer server = new BlockingHttpServer()

    @Issue("https://github.com/gradle/gradle/issues/20081")
    def "test report receives events for disabled parameterized test"() {
        given:
        testClass("PassingWithDisabledParameterizedTest").with {
            testMethod('passingTest')
            testMethod('disabledTest').disabled()
            parameterizedMethod('disabledParameterizedTest').disabled()
            parameterizedMethod('enabledParameterizedTest')
        }
        testClass("OnlyDisabledParameterizedTest").with {
            parameterizedMethod('disabledParameterizedTest1').disabled()
            parameterizedMethod('disabledParameterizedTest2').disabled()
        }
        writeTestClassFiles()

        when:
        run "test"

        then:
        def result = resultsFor(testDirectory)
        result.testPath("PassingWithDisabledParameterizedTest").onlyRoot()
            .assertChildCount(4, 0)
            .assertChildrenSkipped("disabledTest()", "disabledParameterizedTest(String)")
            .assertChildrenExecuted("passingTest()", "enabledParameterizedTest(String)")
        result.testPath(":PassingWithDisabledParameterizedTest:enabledParameterizedTest(String)").onlyRoot()
            .assertChildCount(2, 0)
            .assertChildrenExecuted("enabledParameterizedTest(String)[1]", "enabledParameterizedTest(String)[2]")
        result.testPath("OnlyDisabledParameterizedTest").onlyRoot()
            .assertChildCount(2, 0)
            .assertChildrenSkipped("disabledParameterizedTest1(String)", "disabledParameterizedTest2(String)")
    }

    @Issue("https://github.com/gradle/gradle/issues/20081")
    def "test report receives events for disabled parameterized test within test suite"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org.junit.platform:junit-platform-suite-engine:${LATEST_PLATFORM_VERSION}'
            }
        """

        testSuite("TestSuite").with {
            testClass("PassingWithDisabledParameterizedTest").with {
                testMethod("passingTest")
                testMethod("disabledTest").disabled()
                parameterizedMethod("disabledParameterizedTest").disabled()
                parameterizedMethod("enabledParameterizedTest")
            }
            testClass("OnlyDisabledParameterizedTest").with {
                parameterizedMethod("disabledParameterizedTest1").disabled()
                parameterizedMethod("disabledParameterizedTest2").disabled()
            }
        }

        writeTestClassFiles()

        when:
        run("test", "--tests", "TestSuite")

        then:
        def result = resultsFor(testDirectory)

        result.testPathPreNormalized(":TestSuite:PassingWithDisabledParameterizedTest").onlyRoot()
            .assertChildCount(4, 0)
        result.testPathPreNormalized(":TestSuite:PassingWithDisabledParameterizedTest").onlyRoot()
            .assertChildrenExecuted("passingTest()", "enabledParameterizedTest(String)")
            .assertChildrenSkipped("disabledTest()", "disabledParameterizedTest(String)")
        result.testPathPreNormalized(":TestSuite:PassingWithDisabledParameterizedTest:enabledParameterizedTest(String)").onlyRoot()
            .assertChildrenExecuted("enabledParameterizedTest(String)[1]", "enabledParameterizedTest(String)[2]")
        result.testPathPreNormalized(":TestSuite:OnlyDisabledParameterizedTest").onlyRoot()
            .assertChildCount(2, 0)
            .assertChildrenSkipped("disabledParameterizedTest1(String)", "disabledParameterizedTest2(String)")
    }

    @Issue("https://github.com/gradle/gradle/issues/20081")
    def "test report receives events for skipped parameterized test when there is a failure and fail fast is used"() {
        given:
        server.start()
        buildFile << """
            test { maxParallelForks = 2 }
        """
        testClass("FailingTest")
            .testMethod('failingTest')
                .shouldFail()
                .customContent(server.callFromBuild("failingTest"))
        testClass("WithParameterizedTest")
            .parameterizedMethod('enabledParameterizedTest')
                .customContent(server.callFromBuild("enabledParameterizedTest"))
        writeTestClassFiles()

        def testExecution = server.expectConcurrentAndBlock('failingTest', 'enabledParameterizedTest')

        when:
        def gradle = executer.withTasks('test', '--fail-fast').start()

        then:
        testExecution.waitForAllPendingCalls()

        when:
        testExecution.release('failingTest')

        then:
        gradle.waitForFailure()

        then:
        def result = resultsFor(testDirectory)
        result.assertAtLeastTestPathsExecuted("FailingTest", "WithParameterizedTest")
        result.testPathPreNormalized(":FailingTest:failingTest()").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(CoreMatchers.anything())
        result.testPathPreNormalized(":WithParameterizedTest:enabledParameterizedTest(String):enabledParameterizedTest(String)[1]").onlyRoot()
            .assertHasResult(TestResult.ResultType.SKIPPED)
    }
}
