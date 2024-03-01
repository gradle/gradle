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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo

abstract class AbstractJUnitConsoleLoggingIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    TestFile testFile = file('src/test/java/org/gradle/SomeTest.java')
    abstract String getMaybePackagePrefix()

    def setup() {
        executer.noExtraLogging()
        testFile << """
            package org.gradle;

            ${testFrameworkImports}

            public class SomeTest {
                @Test
                public void goodTest() {}

                @Test
                public void badTest() {
                    beBad();
                }

                @Test
                public void printTest() {
                    System.out.println("line 1\\nline 2");
                    System.out.println("line 3");
                }

                @Test
                ${ignoreOrDisabledAnnotation}
                public void ignoredTest() {
                    throw new RuntimeException("ignored");
                }

                private void beBad() {
                    throw new RuntimeException("bad");
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: "groovy"

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
            }

            test {
                ${configureTestFramework}
                testLogging {
                    quiet {
                        events "skipped", "failed"
                        minGranularity 2
                        maxGranularity -1
                        displayGranularity 3
                        exceptionFormat "full"
                        stackTraceFilters "truncate", "groovy"
                    }
                }
            }
        """
    }

    def "default lifecycle logging"() {
        when:
        fails("test")

        then:
        outputContains("""
            ${maybePackagePrefix}SomeTest > ${maybeParentheses('badTest')} FAILED
                java.lang.RuntimeException at SomeTest.java:${lineNumberOf('RuntimeException("bad")')}
        """.stripIndent())
    }

    def "custom quiet logging"() {
        when:
        executer.withStackTraceChecksDisabled()
        args("-q")
        fails("test")

        then:
        outputContains("""
            ${maybeParentheses('badTest')} FAILED
                java.lang.RuntimeException: bad
                    at org.gradle.SomeTest.beBad(SomeTest.java:${lineNumberOf('throw new RuntimeException("bad")')})
                    at org.gradle.SomeTest.badTest(SomeTest.java:${lineNumberOf('beBad();')})
        """.stripIndent())

        outputContains("${maybeParentheses('ignoredTest')} SKIPPED")

        outputContains("${maybePackagePrefix}SomeTest FAILED")
    }

    def "standard output logging"() {
        given:
        file('src/test/java/org/gradle/StandardOutputTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class StandardOutputTest {
                @Test
                public void goodTest() {}

                @Test
                public void badTest() {
                    beBad() ;
                }

                @Test
                public void printTest() {
                    System.out.println("line 1\\nline 2");
                    System.out.println("line 3");
                }

                @Test
                ${ignoreOrDisabledAnnotation}
                public void ignoredTest() {
                    throw new RuntimeException("ignored");
                }

                private void beBad() {
                    throw new RuntimeException("bad");
                }
            }
        """.stripIndent()
        buildFile << """
            test {
                testLogging {
                    quiet {
                        events "standardOut", "standardError"
                    }
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        args("-q")
        fails("test")

        then:
        outputContains("""
            ${maybeParentheses('printTest')} STANDARD_OUT
                line 1
                line 2
                line 3
        """.stripIndent())
    }

    def "test logging is included in XML results"() {
        file("build.gradle") << """
            apply plugin: 'java'
                ${mavenCentralRepository()}
                dependencies {
                    ${testFrameworkDependencies}
                }
        """.stripIndent()

        file("src/test/java/EncodingTest.java") << """
            ${testFrameworkImports}

            public class EncodingTest {
                @Test public void encodesCdata() {
                    System.out.println("< html allowed, cdata closing token ]]> encoded!");
                    System.out.print("no EOL, ");
                    System.out.println("non-asci char: ż");
                    System.out.println("xml entity: &amp;");
                    System.err.println("< html allowed, cdata closing token ]]> encoded!");
                }
                @Test public void encodesAttributeValues() {
                    throw new RuntimeException("html: <> cdata: ]]>");
                }
            }
        """.stripIndent()

        when:
        fails("test")

        then:
        new DefaultTestExecutionResult(testDirectory)
                .testClass("EncodingTest")
                .assertTestPassed("encodesCdata")
                .assertTestFailed("encodesAttributeValues", equalTo('java.lang.RuntimeException: html: <> cdata: ]]>'))
                .assertStdout(containsString(
                    "< html allowed, cdata closing token ]]> encoded!\n" +
                    "no EOL, non-asci char: ż\n" +
                    "xml entity: &amp;"
                ))
                .assertStderr(equalTo("< html allowed, cdata closing token ]]> encoded!\n"))
    }

    String lineNumberOf(String text) {
        int i = 1
        for (String line : testFile.readLines()) {
            if (line.contains(text)) {
                return i as String
            }
            i++
        }
    }
}
