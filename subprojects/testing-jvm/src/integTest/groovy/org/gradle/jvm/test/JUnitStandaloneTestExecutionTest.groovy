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
        boolean useLib = sourceconfig.hasLibraryDependency
        boolean useExternalDep = sourceconfig.hasExternalDependency

        and:
        testSuiteComponent(sourceconfig)
        if (useLib) {
            utilsLibrary()
        }

        and:
        standaloneTestSuite(true, useLib, useExternalDep)

        when:
        succeeds ':mySuiteTest'

        then:
        executedAndNotSkipped ':compileMySuiteMySuiteMySuiteJava', ':mySuiteTest'
        int testCount = 1;
        def tests = ['test']
        if (useLib) {
            testCount++
            tests << 'testLibDependency'
        }
        if (useExternalDep) {
            testCount++
            tests << 'testExternalDependency'
        };
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('MyTest')
        def check = result.testClass('MyTest')
            .assertTestCount(testCount, 0, 0)
            .assertTestsExecuted(tests as String[])
        tests.each {
            check.assertTestPassed(it)
        }

        where:
        sourceconfig << SourceSetConfiguration.values()
    }

    @Unroll("Executes a failing test suite with a JUnit component and #sourceconfig.description")
    def "executes a failing test suite"() {
        given:
        applyJUnitPlugin()
        boolean useLib = sourceconfig.hasLibraryDependency
        boolean useExternalDep = sourceconfig.hasExternalDependency

        and:
        testSuiteComponent(sourceconfig)
        if (useLib) {
            utilsLibrary()
        }

        and:
        standaloneTestSuite(false, useLib, useExternalDep)

        when:
        fails ':mySuiteTest'

        then:
        executedAndNotSkipped ':compileMySuiteMySuiteMySuiteJava', ':mySuiteTest'
        failure.assertHasCause('There were failing tests. See the report at')
        int testCount = 1;
        def tests = [
            'test': 'java.lang.AssertionError: expected:<true> but was:<false>',
        ]
        if (useLib) {
            testCount++
            tests.testLibDependency = 'java.lang.AssertionError: expected:<0> but was:<666>'
        }
        if (useExternalDep) {
            testCount++
            tests.testExternalDependency = 'org.junit.ComparisonFailure: expected:<[Hello World]> but was:<[oh noes!]>'
        };
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('MyTest')
        def check = result.testClass('MyTest')
            .assertTestCount(testCount, testCount, 0)
            .assertTestsExecuted(tests.keySet() as String[])
        tests.each { test, error ->
            check.assertTestFailed(test, Matchers.equalTo(error))
        }

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
        NONE('no source set is declared', false, false, ''),
        EXPLICIT_NO_DEPS('an explicit source set configuration is used', false, false, '''{
                        sources {
                            java {
                               source.srcDirs 'src/test/java'
                            }
                        }
                    }'''),
        LIBRARY_DEP('a dependency onto a local library', true, false, '''{
                        sources {
                            java {
                                dependencies {
                                    library 'utils'
                                }
                            }
                        }
                    }'''),
        EXTERNAL_DEP('a dependency onto an external library', false, true, '''{
                        sources {
                            java {
                                dependencies {
                                    module 'org.apache.commons:commons-lang3:3.4'
                                }
                            }
                        }
                    }''')
        private final String description
        private final String configuration
        private boolean hasLibraryDependency;
        private boolean hasExternalDependency;

        public SourceSetConfiguration(String description, boolean hasLibraryDependency, boolean hasExternalDependency, String configuration) {
            this.description = description
            this.hasLibraryDependency = hasLibraryDependency
            this.hasExternalDependency = hasExternalDependency
            this.configuration = configuration
        }

    }

    private void testSuiteComponent(SourceSetConfiguration config = SourceSetConfiguration.EXPLICIT_NO_DEPS) {
        buildFile << """
            model {
                components {
                    mySuite(JUnitTestSuiteSpec) ${config.configuration}
                }
            }
        """
    }

    private void utilsLibrary() {
        buildFile << """
            model {
                components {
                    utils(JvmLibrarySpec)
                }
            }
        """.stripMargin()
        file('src/utils/java/Utils.java') << '''public class Utils {
            public static final int MAGIC = 42;
        }'''.stripMargin()
    }

    private void standaloneTestSuite(boolean passing, boolean hasLibraryDependency, boolean hasExternalDependency) {

        // todo: the value '0' is used, where it should in reality be 42, because we're using the API jar when resolving dependencies
        // where we should be using the runtime jar instead. This will be fixed in another story.
        // Meanwhile this will ensure that we can depend on a local library for building a test suite.
        String libraryDependencyTest = """
            @Test
            public void testLibDependency() {
                assertEquals(Utils.MAGIC, ${passing ? '0' : '666'});
            }
        """

        String externalDependencyTest = """
            @Test
            public void testExternalDependency() {
                assertEquals("Hello World", ${passing ? 'org.apache.commons.lang3.text.WordUtils.capitalize("hello world")' : '"oh noes!"'});
            }
        """

        file('src/test/java/MyTest.java') << """
        import org.junit.Test;

        import static org.junit.Assert.*;

        public class MyTest {

            @Test
            public void test() {
                assertEquals(true, ${passing ? 'true' : 'false'});
            }


            ${hasLibraryDependency ? libraryDependencyTest : ''}

            ${hasExternalDependency ? externalDependencyTest : ''}
        }
        """.stripMargin()
    }
}
