/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

// can make assumptions about order in which test methods of TestNGTest get executed
// because the methods are chained with 'methodDependsOn'
class TestNGLoggingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.noExtraLogging().withStackTraceChecksDisabled().withTasks("test")

        buildFile << """
            apply plugin: "groovy"

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "org.codehaus.groovy:groovy-all:2.0.5"
                testCompile "org.testng:testng:6.3.1"
            }

            test {
                useTestNG()
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

        file("src/test/groovy/org/gradle/TestNGTest.groovy") << """
            package org.gradle

            import org.testng.annotations.Test

            class TestNGTest {
                @Test
                void goodTest() {}

                @Test(dependsOnMethods = ["goodTest"])
                void badTest() {
                    beBad()
                }

                @Test(dependsOnMethods = ["badTest"])
                void ignoredTest() {}

                @Test(dependsOnMethods = ["goodTest"])
                void printTest() {
                    println "line 1\\nline 2"
                    println "line 3"
                }

                private beBad() {
                    throw new RuntimeException("bad")
                }
            }
        """
    }

    def "defaultLifecycleLogging"() {
        when:
        result = executer.runWithFailure()

        then:
        outputContains("""
Gradle test > org.gradle.TestNGTest.badTest FAILED
    java.lang.RuntimeException at TestNGTest.groovy:25
        """)
    }

    def customQuietLogging() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
org.gradle.TestNGTest.badTest FAILED
    java.lang.RuntimeException: bad
        at org.gradle.TestNGTest.beBad(TestNGTest.groovy:25)
        at org.gradle.TestNGTest.badTest(TestNGTest.groovy:12)

org.gradle.TestNGTest.ignoredTest SKIPPED

Gradle test FAILED
        """)
    }

    def "standardOutputLogging"() {
        given:
        buildFile.text = """
            apply plugin: "groovy"

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "org.codehaus.groovy:groovy-all:2.0.5"
                testCompile "org.testng:testng:6.3.1"
            }

            test {
                useTestNG()
                testLogging {
                    quiet {
                        events "standardOut", "standardError"
                    }
                }
            }
        """

        and:
        file("src/test/groovy/org/gradle/TestNGStandardOutputTest.groovy") << """
            package org.gradle

            import org.testng.annotations.Test

            class TestNGStandardOutputTest {
                @Test
                void printTest() {
                    println "line 1\\nline 2"
                    println "line 3"
                }
            }
        """

        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
Gradle test > org.gradle.TestNGStandardOutputTest.printTest STANDARD_OUT
    line 1
    line 2
    line 3
        """)
    }

    private void outputContains(String text) {
        assert result.output.contains(TextUtil.toPlatformLineSeparators(text.trim()))
    }


}
