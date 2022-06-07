/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.testsuites.transparency

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.CoreMatchers

@CompileStatic
abstract class AbstractTestSuitesTransparencyIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            import org.gradle.api.plugins.jvm.JvmTestSuite.ProjectTransparencyLevel

            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }

                    integTest(JvmTestSuite) {
                        useJUnitJupiter()
                    }
                }
            }
        """.stripIndent()

        file('src/test/java/org/sample/SampleTest.java') << """
            package org.sample;

            import java.util.Map;
            import org.junit.jupiter.api.Test;

            public class SampleTest {
                @Test
                public void testSample() throws Exception {
                    Map myMap = Sample.newMap();
                    myMap.put("red", "stop");
                }
            }
        """.stripIndent()

        file('src/integTest/java/org/sample/SampleIntegTest.java') << """
            package org.sample;

            import java.util.Map;
            import org.junit.jupiter.api.Test;

            public class SampleIntegTest {
                @Test
                public void testSample() throws Exception {
                    Map myMap = Sample.newMap();
                    myMap.put("red", "stop");
                }
            }
        """.stripIndent()
    }

    protected failsAtCompileDueToMissingProjectClasses(String compileTask) {
        fails(compileTask)
        failure.assertHasErrorOutput("Execution failed for task ':$compileTask'.")
        failure.assertHasErrorOutput("Compilation failed; see the compiler error output for details.")
    }

    protected failsAtRuntimeDueToMissingCollectionsDependency(String suiteName) {
        fails(suiteName)
        def result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', suiteName)
        result.testClass(suiteNameToTestClassName(suiteName)).with {
            assertTestFailed("testSample", CoreMatchers.containsString("java.lang.ClassNotFoundException: org.apache.commons.collections.map.ListOrderedMap"))
        }
    }

    protected failsAtRuntimeDueToMissingCollectionsDependencyNoClassDefFound(String suiteName) {
        fails(suiteName)
        def result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', suiteName)
        result.testClass(suiteNameToTestClassName(suiteName)).with {
            assertTestFailed("testSample", CoreMatchers.containsString("java.lang.NoClassDefFoundError: org/apache/commons/collections/map/ListOrderedMap"))
            assertTestFailed("testSample", CoreMatchers.not(CoreMatchers.containsString("error: cannot find symbol"))) // Sample class is found
        }

    }

    protected successfullyRunsSuite(String suiteName) {
        succeeds(suiteName)
        def result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', suiteName)
        result.assertTestClassesExecuted(suiteNameToTestClassName(suiteName))
    }

    private String suiteNameToTestClassName(String suiteName) {
        return 'org.sample.Sample' + suiteName[0].toUpperCase() + suiteName[1..-1]
    }
}

@CompileStatic
class AbstractCompileDepTestSuitesTransparencyIntegrationTest extends AbstractTestSuitesTransparencyIntegrationTest {
    def setup() {
        buildFile << """
            dependencies {
                compileOnly 'commons-collections:commons-collections:3.2.1'
            }
        """.stripIndent()

        file('src/main/java/org/sample/Sample.java') << """
            package org.sample;

            import java.util.Map;
            import org.apache.commons.collections.map.ListOrderedMap;

            public class Sample {
                public static Map newMap() throws Exception {
                    return new ListOrderedMap();
                }
            }
        """.stripIndent()
    }
}

@CompileStatic
class AbstractRuntimeDepTestSuitesTransparencyIntegrationTest extends AbstractTestSuitesTransparencyIntegrationTest {
    def setup() {
        file('src/main/java/org/sample/Sample.java') << """
            package org.sample;

            import java.util.Map;

            public class Sample {
                public static Map newMap() throws Exception {
                    return (Map) Class.forName("org.apache.commons.collections.map.ListOrderedMap").newInstance();
                }
            }
        """.stripIndent()
    }
}
