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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport
import org.gradle.test.fixtures.file.TestFile

class JvmTestSuitePluginIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {

    def "JVM Test Suites plugin adds outgoing variants for default test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
            plugins {
                id 'java'
            }
            """

        file("src/test/java/SomeTest.java") << """
            import org.junit.Test;

            public class SomeTest {
                @Test public void foo() {}
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        def resultsPath = new TestFile(getTestDirectory(), 'build/test-results/test/binary').getRelativePathFromBase()
        outputContains("""
--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - :Test-test:unspecified
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsPath (artifactType = directory)""".stripIndent())

        and:
        hasIncubatingLegend()
    }

    def "JVM Test Suites plugin adds outgoing variants for custom test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
            plugins {
                id 'java'
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST

                        dependencies {
                            implementation project()
                        }
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        def resultsPath = new TestFile(getTestDirectory(), 'build/test-results/integrationTest/binary').getRelativePathFromBase()
        outputContains("""
--------------------------------------------------
Variant testResultsElementsForIntegrationTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the integrationTest Test Suite's integrationTest target.

Capabilities
    - :Test-integration-test:unspecified
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.target.name = integrationTest
    - org.gradle.testsuite.type        = integration-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsPath (artifactType = directory)""".stripIndent())

        and:
        hasIncubatingLegend()
    }

    def "multiple test suites can use the same test suite type"() {
        file("src/primaryIntTest/java/FooTest.java") << "class FooTest { @org.junit.jupiter.api.Test void test() {} }"
        file("src/secondaryIntTest/java/FooTest.java") << "class FooTest { @org.junit.jupiter.api.Test void test() {} }"
        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    primaryIntTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                        useJUnitJupiter()
                    }

                    secondaryIntTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                        useJUnitJupiter()
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds('primaryIntTest', 'secondaryIntTest')
    }

    def "multiple test suites can use the same test suite type (including the built-in test suite)"() {
        file("src/test/java/FooTest.java") << "class FooTest { @org.junit.jupiter.api.Test void test() {} }"
        file("src/secondaryTest/java/FooTest.java") << "class FooTest { @org.junit.jupiter.api.Test void test() {} }"

        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                    secondaryTest(JvmTestSuite) {
                        testType = TestSuiteType.UNIT_TEST
                        useJUnitJupiter()
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds('test', 'secondaryTest')
    }

    def "multiple test suites can use the same test suite type (using the default type of one suite and explicitly setting the other)"() {
        file("src/integrationTest/java/FooTest.java") << "class FooTest { @org.junit.jupiter.api.Test void test() {} }"
        file("src/secondaryIntegrationTest/java/FooTest.java") << "class FooTest { @org.junit.jupiter.api.Test void test() {} }"
        settingsFile << """rootProject.name = 'Test'"""
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        useJUnitJupiter()
                    }

                    secondaryIntegrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                        useJUnitJupiter()
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds('integrationTest', 'secondaryIntegrationTest')
    }
}
