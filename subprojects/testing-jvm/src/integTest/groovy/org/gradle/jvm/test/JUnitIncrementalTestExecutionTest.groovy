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

import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution

@UnsupportedWithInstantExecution(because = "software model")
class JUnitIncrementalTestExecutionTest extends AbstractJUnitTestExecutionIntegrationSpec {

    def setup() {
        applyJUnitPlugin()
        myTestSuiteSpec()
        writeTestSource()
        writeTestResource()
    }

    def "skips execution when nothing changes"() {
        expect:
        succeeds ':myTestBinaryTest'

        and:
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest'

        and:
        expectDeprecationWarnings()
        succeeds ':myTestBinaryTest'

        and:
        skipped ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest'
    }

    def "re-executes test when source changes"() {
        expect:
        succeeds ':myTestBinaryTest'

        and:
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest'

        when:
        changeTestSource()

        then:
        expectDeprecationWarnings()
        succeeds ':myTestBinaryTest'

        and:
        executedAndNotSkipped ':compileMyTestBinaryMyTestJava', ':myTestBinaryTest'
    }

    def "re-executes test when resource changes"() {
        when:
        succeeds ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':processMyTestBinaryMyTestResources', ':myTestBinaryTest'

        when:
        changeTestResource()

        then:
        expectDeprecationWarnings()
        succeeds ':myTestBinaryTest'

        and:
        executedAndNotSkipped ':processMyTestBinaryMyTestResources', ':myTestBinaryTest'
    }

    def "re-executes test when local library dependency changes"() {
        given:
        utilsLibrary()
        dependencyOnUtilsLibrary()

        when:
        succeeds ':myTestBinaryTest'

        then:
        executedAndNotSkipped ':myUtilsApiJar', ':myTestBinaryTest'

        when:
        expectDeprecationWarnings()
        succeeds ':myTestBinaryTest'

        then:
        skipped ':myUtilsApiJar', ':myTestBinaryTest'

        when:
        updateUtilsLibrary()

        then:
        expectDeprecationWarnings()
        succeeds ':myTestBinaryTest'
        executedAndNotSkipped ':myUtilsApiJar', ':myTestBinaryTest'
    }

    def changeTestSource() {
        writeTestSource("differentTestMethodName")
    }

    def writeTestSource(String testMethodName = "test", boolean passing = true) {
        writeFile("src/myTest/java/MyTest.java", """
            import org.junit.Test;

            import static org.junit.Assert.*;

            public class MyTest {
                @Test
                public void ${testMethodName}() {
                    assertEquals(true, ${passing ? 'true' : 'false'});
                }
            }
        """)
    }

    def changeTestResource() {
        writeTestResource("1.618")
    }

    def writeTestResource(String value = "42") {
        writeFile("src/myTest/resources/data.properties", "value=$value")
    }

    def writeFile(String path, String content) {
        file(path).createFile().write(content)
    }

    void utilsLibrary() {
        buildFile << '''
            model {
                components {
                    myUtils(JvmLibrarySpec)
                }
            }
        '''
        file('src/myUtils/java/Utils.java') << '''
            public class Utils {
                public static void foo(int x) { }
            }
        '''
    }

    void dependencyOnUtilsLibrary() {
        buildFile << '''
            model {
                testSuites {
                    myTest {
                        sources {
                            java {
                                dependencies {
                                    library 'myUtils'
                                }
                            }
                        }
                    }
                }
            }
        '''
    }

    void updateUtilsLibrary() {
        file('src/myUtils/java/Utils.java').write '''
            public class Utils {
                public static void foo(int x, int y) { } // change public API
            }
        '''
    }
}
