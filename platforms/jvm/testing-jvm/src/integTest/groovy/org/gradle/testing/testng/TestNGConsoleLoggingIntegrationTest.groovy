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
import spock.lang.Issue

// can make assumptions about order in which test methods of TestNGTest get executed
// because the methods are chained with 'methodDependsOn'
class TestNGConsoleLoggingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.noExtraLogging()

        buildFile << """
            apply plugin: "groovy"

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.codehaus.groovy:groovy:2.4.10"
            }

            testing {
                suites {
                    test {
                        useTestNG('6.3.1')

                        targets {
                            all {
                                testTask.configure {
                                    testLogging {
                                        quiet {
                                            events "skipped", "failed"
                                            minGranularity = 2
                                            maxGranularity = -1
                                            displayGranularity = 3
                                            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                                            stackTraceFilters "truncate", "groovy"
                                        }
                                    }
                                }
                            }
                        }
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

    def "can log with default lifecycle logging"() {
        when:
        fails "test"

        then:
        outputContains("""
Gradle test > org.gradle.TestNGTest.badTest FAILED
    java.lang.RuntimeException at TestNGTest.groovy:25
        """)
    }

    def "can log with custom quiet logging"() {
        when:
        executer.withStackTraceChecksDisabled()
        args "-q"
        fails "test"

        then:
        outputContains("""
Gradle test > org.gradle.TestNGTest.badTest FAILED
    java.lang.RuntimeException: bad
        at org.gradle.TestNGTest.beBad(TestNGTest.groovy:25)
        at org.gradle.TestNGTest.badTest(TestNGTest.groovy:12)

Gradle test > org.gradle.TestNGTest.ignoredTest SKIPPED

Gradle test FAILED

Gradle suite FAILED
        """)
    }

    def "can log to stdout"() {
        given:
        buildFile.text = """
            apply plugin: "groovy"

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.codehaus.groovy:groovy:2.4.10"
            }

            testing {
                suites {
                    test {
                        useTestNG('6.3.1')

                        targets {
                            all {
                                testTask.configure {
                                    testLogging {
                                        quiet {
                                            events "standardOut", "standardError"
                                        }
                                    }
                                }
                            }
                        }
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
        args "-q"
        fails "test"

        then:
        outputContains("""
Gradle test > org.gradle.TestNGStandardOutputTest.printTest STANDARD_OUT
    line 1
    line 2
    line 3
        """)
    }

    @Issue("https://github.com/gradle/gradle/issues/25857")
    def "failure during TestNG initialization is written to console when granularity is set"() {
        given:
        buildFile.text = """
            apply plugin: "groovy"

            ${mavenCentralRepository()}

            dependencies {
                implementation "org.codehaus.groovy:groovy:2.4.10"
            }

            testing {
                suites {
                    test {
                        useTestNG('6.3.1')

                        targets {
                            all {
                                testTask.configure {
                                    options {
                                        listeners.add("com.listeners.DoesNotExist")
                                    }
                                    testLogging {
                                        minGranularity = 1
                                        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        fails "test"

        then:
        outputContains("org.gradle.api.GradleException: Could not add a test listener with class 'com.listeners.DoesNotExist'")
        outputContains("java.lang.ClassNotFoundException: com.listeners.DoesNotExist")
    }

}
