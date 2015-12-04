/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.test
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.Matchers

class JUnitStandaloneTestExecutionTest extends AbstractIntegrationSpec {

    def "should apply junit plugin using explicit class reference"() {
        given:
        applyJUnitPlugin()

        when:
        run 'tasks'

        then:
        noExceptionThrown()
    }

    def "creates a JUnit test suite binary"() {
        given:
        applyJUnitPlugin()

        and:
        buildFile << '''
            model {
                components {
                    mySuite(JUnitTestSuiteSpec)
                }
            }
        '''

        when:
        run 'components'

        then:
        noExceptionThrown()

        and:
        outputContains "Test 'mySuite:mySuite'"
    }

    def "compiles a test case"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        standaloneTestCase()

        when:
        succeeds ':compileMySuiteMySuiteMySuiteJava'

        then:
        executedAndNotSkipped ':compileMySuiteMySuiteMySuiteJava'

    }

    def "executes a passing test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        standaloneTestCase(true)

        when:
        succeeds ':mySuiteTest'

        then:
        executedAndNotSkipped ':compileMySuiteMySuiteMySuiteJava', ':mySuiteTest'
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('MyTest')
        result.testClass('MyTest')
            .assertTestCount(1, 0, 0)
            .assertTestsExecuted('test')
            .assertTestPassed('test')
    }

    def "executes a failing test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent()

        and:
        standaloneTestCase(false)

        when:
        fails ':mySuiteTest'

        then:
        executedAndNotSkipped ':compileMySuiteMySuiteMySuiteJava', ':mySuiteTest'
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('MyTest')
        result.testClass('MyTest')
            .assertTestCount(1, 1, 0)
            .assertTestsExecuted('test')
            .assertTestFailed('test', Matchers.equalTo('java.lang.AssertionError: expected:<true> but was:<false>'))
    }

    private TestFile applyJUnitPlugin() {
        buildFile << '''import org.gradle.jvm.plugins.JUnitTestSuitePlugin
            plugins {
                id 'jvm-component'
                id 'java-lang'
                id 'junit-test-suite'
            }

            repositories {
                jcenter()
            }
        '''
    }

    private TestFile testSuiteComponent() {
        buildFile << '''
            model {
                components {
                    mySuite(JUnitTestSuiteSpec) {
                        sources {
                            java(JavaSourceSet) {
                               source.srcDirs 'src/test/java'
                            }
                        }
                    }
                }
            }
        '''
    }

    private TestFile standaloneTestCase(boolean passing = true) {
        file('src/test/java/MyTest.java') << """
        import org.junit.Test;

        import static org.junit.Assert.*;

        public class MyTest {

            @Test
            public void test() {
                assertEquals(true, ${passing ? 'true' : 'false'});
            }
        }
        """.stripMargin()
    }
}
