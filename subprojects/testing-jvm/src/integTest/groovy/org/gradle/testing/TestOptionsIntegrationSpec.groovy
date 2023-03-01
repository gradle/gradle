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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HtmlTestExecutionResult

/**
 * These tests demonstrate what is and isn't allowed in terms of modifying the {@link org.gradle.api.tasks.testing.TestFrameworkOptions TestFrameworkOptions}
 * provided to a {@link org.gradle.api.tasks.testing.Test Test} task.
 */
class TestOptionsIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
        plugins {
            id 'java'
        }

        ${mavenCentralRepository()}
        """
        file("src/test/java/org/example/SomeTestClass.java") << """
package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("fast")
public class SomeTestClass {
    @Test
    public void ok1() {
    }

    @Test
    public void ok2() {
    }
}
        """
        file("src/integTest/java/org/example/SomeIntegTestClass.java") << """
package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("fast")
public class SomeIntegTestClass {
    @Test
    public void ok1() {
    }

    @Test
    public void ok2() {
    }
}
        """
    }
    // region default test suite
    def "can NOT set options and then change framework within the default suite"() {
        given:
        buildFile << """
        testing {
           suites {
               test {
                   targets.all {
                       testTask.configure {
                           options {
                               // forces the test framework to be JUnit
                               includeCategories 'org.gradle.CategoryA'
                           }
                           useJUnitPlatform()
                       }
                   }
               }
           }
        }""".stripMargin()

        expect:
        fails ":test"
        failure.assertHasCause("The value for task ':test' property 'testFrameworkProperty' is final and cannot be changed any further.")
    }

    def "can set test framework in default test suite prior to setting options within test task"() {
        given:
        buildFile << """
        // Configure task through suite
        testing {
            suites {
                test {
                    useJUnitJupiter()
                    targets.all {
                        // explicitly realize the task now to cause this configuration to run now
                        testTask.get().configure {
                            options {
                                includeTags 'fast'
                            }
                        }
                    }
                }
            }
        }
        """.stripMargin()

        expect:
        succeeds ":test"
        assertTestsExecuted()
    }

    def "can set options on default test task directly, outside of default test suite, then again inside suite"() {
        given:
        buildFile << """
        test {
           useJUnitPlatform()
           options {
               includeTags 'fast'
           }
        }

        testing {
           suites {
               test {
                   useJUnitJupiter()
                   targets.all {
                       testTask.configure {
                            options {
                                includeTags 'fast', 'medium'
                            }
                        }
                   }
               }
           }
        }""".stripMargin()

        expect:
        succeeds ":test"
        assertTestsExecuted()
    }

    // NOTE: This captures current behavior. It would be better if we could prevent the built-in test task from changing
    def "can change test framework for default test suite to something different from the test suite"() {
        given:
        buildFile << """
        testing {
           suites {
               test {
                   useTestNG()
                   targets.all {
                       testTask.configure {
                           useJUnitPlatform()
                        }
                   }
               }
           }
        }""".stripMargin()

        expect:
        fails ":test"
        failure.assertHasCause("Compilation failed; see the compiler error output for details.")
    }

    def "can toggle framework multiple times on default test task directly, outside of default test suite"() {
        given:
        buildFile << """
        dependencies {
            implementation 'org.junit.jupiter:junit-jupiter:5.7.1'
        }

        test {
           useJUnitPlatform()
           useJUnit()
           useJUnitPlatform()
           options {
               includeTags 'fast'
           }
        }""".stripMargin()

        expect:
        succeeds ":test"
        assertTestsExecuted()
    }

    def "can toggle framework multiple times on default test task directly, prior to setting options via default test suite"() {
        given:
        buildFile << """
        testing {
           suites {
               test {
                   useJUnitJupiter()
                   useJUnit()
                   useJUnitJupiter()
                   targets.all {
                       testTask.configure {
                            options {
                               includeTags 'fast'
                           }
                        }
                    }
               }
           }
        }""".stripMargin()

        expect:
        succeeds ":test"
        assertTestsExecuted()
    }
    // endregion default test suite

    // region custom test suite
    def "can toggle framework multiple times on custom test suite prior to setting options"() {
        given:
        buildFile << """
        testing {
           suites {
               integTest(JvmTestSuite) {
                   useJUnitJupiter()
                   useJUnit()
                   useJUnitJupiter()
                   targets.all {
                       testTask.configure {
                            options {
                               includeTags 'fast'
                           }
                       }
                   }
               }
           }
        }""".stripMargin()

        expect:
        succeeds ":integTest"
        assertIntegTestsExecuted()
    }

    def "can NOT set new framework for custom suite in different testing block after configuring options"() {
        buildFile << """
            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        targets.all {
                            // explicitly realize the task now to cause this configuration to run now
                            testTask.get().configure {
                                options {
                                    excludeCategories "com.example.Exclude"
                                }
                            }
                        }
                    }
                }
            }

            testing.suites.integTest {
                useTestNG()
            }
        """

        when:
        fails("integTest")

        then:
        failure.assertHasCause("The value for property 'testSuiteTestingFramework' is final and cannot be changed any further.")
    }

    def "can NOT set test framework in test task when created by test suite"() {
        given:
        buildFile << """
        // Ensure non-default suites exist
        testing {
           suites {
               integTest(JvmTestSuite)
            }
        }

        // Configure task directly using name
        integTest {
            useJUnitPlatform()
        }""".stripMargin()

        expect:
        fails ":integTest"
        failure.assertHasCause("The value for task ':integTest' property 'testFrameworkProperty' cannot be changed any further.")
    }
    // endregion custom test suite

    // region all suites
    def "can set test framework in #suiteName suite prior to setting options within test task"() {
        given:
        buildFile << """
        // Configure task through suite
        testing {
            suites {
                integTest(JvmTestSuite) {
                    useJUnitJupiter()
                    targets.all {
                        // explicitly realize the task now to cause this configuration to run now
                        testTask.get().configure {
                            options {
                                includeTags 'fast'
                            }
                        }
                    }
                }
            }
        }
        """.stripMargin()

        expect:
        succeeds ":integTest"
        assertIntegTestsExecuted()
    }

    // See JUnitCategoriesIntegrationSpec for the inspiration for this test
    def "re-executes test when changing options"() {
        given:
        buildFile << """
        testing {
           suites {
               integTest(JvmTestSuite) {
                   useJUnitJupiter()
                   targets {
                       all {
                           testTask.configure {
                               options {
                                   includeTags 'fast'
                               }
                           }
                       }
                   }
               }
           }
        }""".stripMargin()

        when:
        succeeds ":integTest"
        assertIntegTestsExecuted()

        then:
        executedAndNotSkipped ":integTest"

        when:
        buildFile << """
        integTest {
           options {
               includeTags 'slow'
           }
        }""".stripMargin()

        and:
        succeeds ":integTest"
        assertIntegTestsExecuted()

        then:
        executedAndNotSkipped ":integTest"
    }
    // endregion all suites

    // region stand-alone test tasks
    def "can toggle framework multiple times on custom test task unrelated to suites"() {
        given:
        buildFile << """
        tasks.register('integTest', Test) {
           useJUnitPlatform()
           useJUnit()
           useJUnitPlatform()
           options {
               includeTags 'fast'
           }
        }

        assert integTest.options instanceof JUnitPlatformOptions
        """.stripMargin()

        expect:
        succeeds "help"
    }

    def "can set built-in test task to use the same framework it was using after setting options"() {
        given:
        buildFile << """
        test {
           useJUnit()
           options {
               includeCategories 'fast'
           }
           useJUnit()
        }

        assert test.options instanceof JUnitOptions
        """.stripMargin()

        expect:
        succeeds "help"
    }
    // endregion stand-alone test tasks

    private void assertTestsExecuted() {
        def result = new HtmlTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("org.example.SomeTestClass")
    }

    private void assertIntegTestsExecuted() {
        def result = new HtmlTestExecutionResult(testDirectory, "build/reports/tests/integTest")
        result.assertTestClassesExecuted("org.example.SomeIntegTestClass")
    }
}
