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

class JvmTestSuitePluginIntegrationTest extends AbstractIntegrationSpec {

    def "JVM Test Suites plugin adds outgoing variants for default test suite"() {
        buildFile << """
            plugins {
                id 'jvm-test-suite'
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

        outputContains("""
            --------------------------------------------------
            Variant testDataElementsForTest
            --------------------------------------------------
            Capabilities
                - :${getTestDirectory().getName()}:unspecified (default capability)
            Attributes
                - org.gradle.category      = documentation
                - org.gradle.docstype      = test-results-bin
                - org.gradle.targetname    = test
                - org.gradle.testsuitename = test
                - org.gradle.testsuitetype = unit-tests
                - org.gradle.usage         = verification

            Artifacts
                - build${File.separator}test-results${File.separator}test${File.separator}binary${File.separator}results.bin (artifactType = binary)
            """.stripIndent())
    }

    def "JVM Test Suites plugin adds outgoing variants for custom test suite"() {
        buildFile << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testType = TestType.INTEGRATION_TESTS

                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        outputContains("""
            --------------------------------------------------
            Variant testDataElementsForIntegrationTest
            --------------------------------------------------
            Capabilities
                - :${getTestDirectory().getName()}:unspecified (default capability)
            Attributes
                - org.gradle.category      = documentation
                - org.gradle.docstype      = test-results-bin
                - org.gradle.targetname    = integrationTest
                - org.gradle.testsuitename = integrationTest
                - org.gradle.testsuitetype = integration-tests
                - org.gradle.usage         = verification

            Artifacts
                - build${File.separator}test-results${File.separator}integrationTest/binary/results.bin (artifactType = binary)
            """.stripIndent())
    }

    def "Test coverage data can be consumed by another task via Dependency Management"() {
        buildFile << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        file("src/test/java/SomeTest.java") << """
            import org.junit.Test;

            public class SomeTest {
                @Test public void foo() {}
            }
            """.stripIndent()

        buildFile << """
            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.VERIFICATION))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.TEST_RESULTS))
                }
            }

            dependencies {
                testData project
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert testDataConfig.getResolvedConfiguration().getFiles()*.getName() == [test.binaryResultsDirectory.file("results.bin").get().getAsFile().getName()]
                }
            }""".stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "Test results data can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        def buildFileA = subADir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        file("src/test/java/SomeTestA.java") << """
            import org.junit.Test;

            public class SomeTestA {
                @Test public void foo() {}
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        def buildFileB = subBDir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        file("src/test/java/SomeTestB.java") << """
            import org.junit.Test;

            public class SomeTestB {
                @Test public void foo() {}
            }
            """.stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':subA')
                implementation project(':subB')
            }

            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.VERIFICATION))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.TEST_RESULTS))
                }
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert testDataConfig.getResolvedConfiguration().getFiles().containsAll([project(':subA').tasks["test"].binaryResultsDirectory.file("results.bin").get().getAsFile(),
                                                                                             project(':subB').tasks["test"].binaryResultsDirectory.file("results.bin").get().getAsFile()])
                }
            }

            """
        expect:
        succeeds('testResolve')
    }
}
