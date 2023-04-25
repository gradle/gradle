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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.CoreMatchers

class JUnit4RerunPreviousFailedTestIntegrationTest extends AbstractIntegrationSpec {
    private static final String INDEX_OF_TEST_TO_FAIL = "index.of.test.to.fail"
    private static final List<Integer> TESTS = [1, 2, 3]
    private static final List<String> TEST_CLASSES = TESTS.collect { "ConditionalFailingTest_${it}".toString() }

    def setup() {
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}


            dependencies {
                testImplementation 'junit:junit:4.13'
            }
        """

        TESTS.each {
            file("src/test/java/ConditionalFailingTest_${it}.java") << """
                import org.junit.Test;
                public class ConditionalFailingTest_${it} {
                    @Test
                    public void failedTest() {
                        System.out.println("Test index " + ${it});
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

    def 'subsequent execution runs failed test first'() {
        given:
        letTestFail(indexOfTestToFail)

        when:
        fails('test', '--info')

        then:
        testFailed(indexOfTestToFail)

        when:
        fails('test', '--info')

        then:
        failedTestAreRerunFirst(indexOfTestToFail)

        when:
        letTestFail(0)
        succeeds('test')

        then:
        allTestsSucceed()

        where:
        indexOfTestToFail << TESTS
    }

    void failedTestAreRerunFirst(int failedTestIndex) {
        List<String> lines = output.readLines()
        boolean findIt = false
        for (String line in lines) {
            if (line.contains("Test index ${failedTestIndex}")) {
                findIt = true
                break
            } else if (line.contains("Test index")) {
                assert false
            }
        }

        assert findIt
    }


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
            .testClass("ConditionalFailingTest_${indexOfTestToFail}").assertTestFailed('failedTest', CoreMatchers.anything())
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
}
