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
Binary results obtained from running all targets in the 'test' Test Suite.

Capabilities
    - :Test:unspecified (default capability)
Attributes
    - org.gradle.category         = verification
    - org.gradle.testsuite.name   = test
    - org.gradle.verificationtype = test-results
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
Binary results obtained from running all targets in the 'integrationTest' Test Suite.

Capabilities
    - :Test:unspecified (default capability)
Attributes
    - org.gradle.category         = verification
    - org.gradle.testsuite.name   = integrationTest
    - org.gradle.verificationtype = test-results
Artifacts
    - $resultsPath (artifactType = directory)""".stripIndent())

        and:
        hasIncubatingLegend()
    }

    def "Test suites in different projects can have the same name"() {
        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integrationTest(JvmTestSuite)
                }
            }""".stripIndent()

        def subBDir = createDir("subB")
        subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integrationTest(JvmTestSuite)
                }
            }""".stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            tasks.register('allIntegrationTests') {
                dependsOn(':subA:integrationTest', ':subB:integrationTest')
            }
            """.stripIndent()

        expect:
        succeeds('allIntegrationTests')
    }
}
