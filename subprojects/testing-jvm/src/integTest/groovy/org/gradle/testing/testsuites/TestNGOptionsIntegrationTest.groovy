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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile

class TestNGOptionsIntegrationTest extends AbstractIntegrationSpec {
    def "options for test framework are respected for TestNG in built-in test suite"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useTestNG()
                        targets.all {
                            testTask.configure {
                                options {
                                    excludeGroups "exclude"
                                }
                            }
                        }
                    }
                }
            }
            
            check.dependsOn testing.suites
        """
        writeTestNGSources(file("src/test/java"))

        when:
        succeeds("check")
        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("com.example.IncludedTest")
        result.assertTestClassesNotExecuted("com.example.ExcludedTest")
    }

    def "options for test framework are respected for TestNG for custom test suite"() {
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useTestNG()
                        targets.all {
                            testTask.configure {
                                options {
                                    excludeGroups "exclude"
                                }
                            }
                        }
                    }
                }
            }
            
            check.dependsOn testing.suites
        """
        writeTestNGSources(file("src/integrationTest/java"))

        when:
        succeeds("check")
        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'integrationTest')
        result.assertTestClassesExecuted("com.example.IncludedTest")
        result.assertTestClassesNotExecuted("com.example.ExcludedTest")
    }

    private void writeTestNGSources(TestFile sourcePath) {
        sourcePath.file("com/example/IncludedTest.java") << """
package com.example;

import org.testng.annotations.Test;

public class IncludedTest {
    @Test
    public void testOK() {
    }
}
"""
        sourcePath.file("com/example/ExcludedTest.java") << """
package com.example;

import org.testng.annotations.Test;

public class ExcludedTest {
    @Test(groups = { "exclude" })
    public void testOK() {
    }
}
"""
    }
}
