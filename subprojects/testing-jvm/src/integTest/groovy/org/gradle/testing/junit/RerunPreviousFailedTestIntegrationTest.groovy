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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.Matchers
import spock.lang.Unroll

class RerunPreviousFailedTestIntegrationTest extends AbstractIntegrationSpec {
    static final String INDEX_OF_TEST_TO_FAIL = "index.of.test.to.fail"
    static final List<Integer> TESTS = [1, 2, 3]
    static final List<String> TEST_CLASSES = TESTS.collect { "ConditionalFailingTest_${it}".toString() }

    def setup() {
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}


            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """

        TESTS.each {
            file("src/test/java/ConditionalFailingTest_${it}.java") << """
                import org.junit.Test;
                public class ConditionalFailingTest_${it} {
                    @Test
                    public void failedTest() {
                        if("${it}".equals(System.getProperty("${INDEX_OF_TEST_TO_FAIL}"))) {
                            throw new RuntimeException();
                        }
                    }
                }
            """.stripIndent()
        }
    }

    def letTestFail(def index) {
        buildFile << """
        test {
            systemProperty('${INDEX_OF_TEST_TO_FAIL}', '${index}')
        }
        """
    }

    @Unroll
    def 'subsequent execution runs failed test first'() {
        given:
        letTestFail(indexOfTestToFail)

        when:
        fails('test')

        then:
        testFailed(indexOfTestToFail)

        when:
        fails('test', '--fail-fast')

        then:
        testFailedAndOthersIgnoredOrNotExecuted(indexOfTestToFail)

        when:
        letTestFail(0)
        succeeds('test')

        then:
        allTestsSucceed()

        where:
        indexOfTestToFail << TESTS
    }

    @Unroll
    def 'can delete previous failed test'() {
        given:
        letTestFail(indexOfTestToFail)

        when:
        fails('test')

        then:
        testFailed(indexOfTestToFail)

        when:
        file("src/test/java/ConditionalFailingTest_${indexOfTestToFail}.java").delete()
        succeeds('test')

        then:
        remainTestsSucceed(indexOfTestToFail)

        where:
        indexOfTestToFail << TESTS
    }

    @Unroll
    def 'can modify previous failed test'() {
        given:
        letTestFail(indexOfTestToFail)

        when:
        fails('test')

        then:
        testFailed(indexOfTestToFail)

        when:
        file("src/test/java/ConditionalFailingTest_${indexOfTestToFail}.java").text = """
        public class ConditionalFailingTest_${indexOfTestToFail} {
            public void failedTest() {
            }
        }
        """
        succeeds('test')

        then:
        remainTestsSucceed(indexOfTestToFail)

        where:
        indexOfTestToFail << TESTS
    }

    void testFailed(def indexOfTestToFail) {
        new DefaultTestExecutionResult(testDirectory)
            .testClass("ConditionalFailingTest_${indexOfTestToFail}").assertTestFailed('failedTest', Matchers.anything())
    }

    void allTestsSucceed() {
        new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted(TEST_CLASSES as String[])
    }

    void remainTestsSucceed(def indexOfTestToFail) {
        def failedTest = "ConditionalFailingTest_${indexOfTestToFail}".toString()
        def testClasses = TEST_CLASSES.clone()
        testClasses.remove(failedTest)
        new DefaultTestExecutionResult(testDirectory)
            .assertTestClassesExecuted(testClasses as String[])
    }

    void testFailedAndOthersIgnoredOrNotExecuted(def indexOfTestToFail) {
        def failedTestClass = "ConditionalFailingTest_${indexOfTestToFail}".toString()
        def testClasses = TEST_CLASSES.clone()
        testClasses.remove(failedTestClass)

        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass(failedTestClass).assertTestFailed('failedTest', Matchers.anything())
        testClasses.each {
            if (result.testClassExists(it)) {
                result.testClass(it).assertTestSkipped('failedTest')
            }
        }
    }
}
