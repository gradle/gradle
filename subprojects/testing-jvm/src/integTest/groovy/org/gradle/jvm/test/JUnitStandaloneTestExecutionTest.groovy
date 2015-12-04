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
import spock.lang.Unroll

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

    @Unroll("Executes a passing test suite with a JUnit component and #sourceconfig.description")
    def "executes a passing test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent(sourceconfig)

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

        where:
        sourceconfig << SourceSetConfiguration.values()
    }

    @Unroll("Executes a failing test suite with a JUnit component and #sourceconfig.description")
    def "executes a failing test suite"() {
        given:
        applyJUnitPlugin()

        and:
        testSuiteComponent(sourceconfig)

        and:
        standaloneTestCase(false)

        when:
        fails ':mySuiteTest'

        then:
        executedAndNotSkipped ':compileMySuiteMySuiteMySuiteJava', ':mySuiteTest'
        failure.assertHasCause('There were failing tests. See the report at')
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('MyTest')
        result.testClass('MyTest')
            .assertTestCount(1, 1, 0)
            .assertTestsExecuted('test')
            .assertTestFailed('test', Matchers.equalTo('java.lang.AssertionError: expected:<true> but was:<false>'))

        where:
        sourceconfig << SourceSetConfiguration.values()
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

    private enum SourceSetConfiguration {
        NONE('no source set is declared', ''),
        EXPLICIT_NO_DEPS('an explicit source set configuration is used', '''{
                        sources {
                            java {
                               source.srcDirs 'src/test/java'
                            }
                        }
                    }''')
        private final String description
        private final String configuration

        public SourceSetConfiguration(String description, String configuration) {
            this.description = description
            this.configuration = configuration
        }

    }

    private TestFile testSuiteComponent(SourceSetConfiguration config = SourceSetConfiguration.EXPLICIT_NO_DEPS) {
        buildFile << """
            model {
                components {
                    mySuite(JUnitTestSuiteSpec) ${config.configuration}
                }
            }
        """
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
