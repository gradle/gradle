/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.testsuites


import org.gradle.test.fixtures.file.TestFile

class JUnitJupiterOptionsIntegrationTest extends AbstractTestFrameworkOptionsIntegrationTest {
    def "options for test framework are respected for JUnitJupiter in built-in test suite"() {
        buildFile << """
            testing {
                suites {
                    test {
                        useJUnitJupiter()
                        targets.all {
                            testTask.configure {
                                options {
                                    excludeTags "exclude"
                                }
                            }
                        }
                    }
                }
            }
        """
        writeSources(file("src/test/java"))

        when:
        succeeds("check")
        then:
        assertTestsWereExecutedAndExcluded()
    }

    def "options for test framework are respected for JUnitJupiter for custom test suite"() {
        buildFile << """
            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnitJupiter()
                        targets.all {
                            testTask.configure {
                                options {
                                    excludeTags "exclude"
                                }
                            }
                        }
                    }
                }
            }
        """
        writeSources(file("src/integrationTest/java"))

        when:
        succeeds("check")
        then:
        assertIntegrationTestsWereExecutedAndExcluded()
    }

    def "options for test framework are respected for JUnitJupiter for custom test suite without explicit configuration"() {
        buildFile << """
            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        targets.all {
                            testTask.configure {
                                options {
                                    excludeTags "exclude"
                                }
                            }
                        }
                    }
                }
            }
        """
        writeSources(file("src/integrationTest/java"))

        when:
        succeeds("check")
        then:
        assertIntegrationTestsWereExecutedAndExcluded()
    }

    def "options for test framework are respected for JUnitJupiter for custom test suite with changing test framework"() {
        buildFile << """
            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnit() // use JUnit4
                        useJUnitJupiter() // on second thought, use JUnit Jupiter
                        targets.all {
                            testTask.configure {
                                options {
                                    // assumes JUnit Jupiter
                                    excludeTags "exclude"
                                }
                            }
                        }
                    }
                }
            }
        """
        writeSources(file("src/integrationTest/java"))

        when:
        succeeds("check")
        then:
        assertIntegrationTestsWereExecutedAndExcluded()
    }

    @Override
    void writeSources(TestFile sourcePath) {
        sourcePath.file("com/example/IncludedTest.java") << """
package com.example;
import org.junit.jupiter.api.Test;

public class IncludedTest {
    @Test
    public void testOK() {
    }
}
"""
        sourcePath.file("com/example/ExcludedTest.java") << """
package com.example;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("exclude")
public class ExcludedTest {
    @Test
    public void testOK() {
    }
}
"""
    }
}
