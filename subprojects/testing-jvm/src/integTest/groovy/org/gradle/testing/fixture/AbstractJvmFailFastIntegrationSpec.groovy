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

package org.gradle.testing.fixture

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.hamcrest.Matchers
import org.junit.Rule
import spock.lang.Unroll

abstract class AbstractJvmFailFastIntegrationSpec extends AbstractIntegrationSpec {
    protected static final String FAILED_RESOURCE = "fail"
    protected static final String OTHER_RESOURCE = "other"
    protected static final int MAX_WORKERS = 2

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "all tests run without fail fast"() {
        given:
        withBuildFile()
        withFailingTest()
        withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(2, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        sleep(1000)
        testExecution.release(OTHER_RESOURCE)
        gradleHandle.waitForFailure()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.FailedTest').assertTestFailed('failTest', Matchers.anything())
        result.testClass('pkg.OtherTest').assertTestPassed('passingTest')
    }

    def "all tests run with --no-fail-fast"() {
        given:
        withBuildFile()
        buildFile << "test { failFast = true }"
        withFailingTest()
        withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(2, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test', '--no-fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        sleep(1000)
        testExecution.release(OTHER_RESOURCE)
        gradleHandle.waitForFailure()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.FailedTest').assertTestFailed('failTest', Matchers.anything())
        result.testClass('pkg.OtherTest').assertTestPassed('passingTest')
    }

    def "stop test execution with failFast"() {
        given:
        withBuildFile()
        buildFile << "test { failFast = true }"
        withFailingTest()
        withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(2, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        sleep(1000)
        testExecution.release(OTHER_RESOURCE)
        gradleHandle.waitForFailure()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.FailedTest').assertTestFailed('failTest', Matchers.anything())
        result.testClass('pkg.OtherTest').assertTestCount(0, 0, 0)
    }

    def "stop test execution with --fail-fast"() {
        given:
        withBuildFile()
        withFailingTest()
        withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(2, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        sleep(1000)
        testExecution.release(OTHER_RESOURCE)
        gradleHandle.waitForFailure()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass('pkg.FailedTest').assertTestFailed('failTest', Matchers.anything())
        result.testClass('pkg.OtherTest').assertTestCount(0, 0, 0)
    }

    @Unroll
    def "ensure fail fast with forkEvery #forkEvery, maxWorkers #maxWorkers, omittedTests #testOmitted"() {
        given:
        withBuildFile(maxWorkers, forkEvery)
        withFailingTest()
        def otherResources = withNonfailingTests(testOmitted)
        def testExecution = server.expectOptionalAndBlock(maxWorkers, ([FAILED_RESOURCE ] + otherResources).grep() as String[])

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
        forkEvery | maxWorkers | testOmitted
        0         | 1          | 1
        1         | 1          | 1
        2         | 1          | 1
        0         | 2          | 5
        1         | 2          | 5
        2         | 2          | 5
    }

    def "fail fast console output shows failure"() {
        given:
        withBuildFile()
        withFailingTest()
        withNonfailingTest()
        def testExecution = server.expectConcurrentAndBlock(2, FAILED_RESOURCE, OTHER_RESOURCE)

        when:
        def gradleHandle = executer.withTasks('test', '--fail-fast').start()
        testExecution.waitForAllPendingCalls()

        then:
        testExecution.release(FAILED_RESOURCE)
        gradleHandle.waitForFailure()
        assert gradleHandle.standardOutput.matches(/(?s).*pkg\.FailedTest.*failTest.*FAILED.*java.lang.RuntimeException at FailedTest.java.*/)
        assert !gradleHandle.standardOutput.contains('pkg.OtherTest')
    }

    void withBuildFile(int maxWorkers = MAX_WORKERS, int forkEvery = 0) {
        buildFile << """
            apply plugin: 'java'

            ${RepoScriptBlockUtil.jcenterRepository()}

            dependencies {
                testCompile '${testDependency()}'
            }

            tasks.withType(Test) {
                maxParallelForks = $maxWorkers
                forkEvery = $forkEvery
            }

            ${testFrameworkConfiguration()}
        """
    }

    void withFailingTest() {
        file('src/test/java/pkg/FailedTest.java') << """
            package pkg;
            import ${testAnnotationClass()};
            public class FailedTest {
                @Test
                public void failTest() {
                    ${server.callFromBuild("$FAILED_RESOURCE")}
                    throw new RuntimeException();
                }
            }
        """.stripIndent()
    }

    void withNonfailingTest() {
        file('src/test/java/pkg/OtherTest.java') << """
            package pkg;
            import ${testAnnotationClass()};
            public class OtherTest {
                @Test
                public void passingTest() {
                    ${server.callFromBuild("$OTHER_RESOURCE")}
                }
            }
        """.stripIndent()
    }

    List<String> withNonfailingTests(int num) {
        (1..num).collect {
            final resource = "test_${it}" as String
            file("src/test/java/pkg/OtherTest_${it}.java") << """
                package pkg;
                import ${testAnnotationClass()};
                public class OtherTest_${it} {
                    @Test
                    public void passingTest() {
                        ${server.callFromBuild("$resource")}
                    }
                }
            """.stripIndent()
            resource
        }
    }

    abstract String testAnnotationClass()
    abstract String testDependency()
    abstract String testFrameworkConfiguration()
}
