/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes

import static org.junit.Assume.assumeNotNull

class TestSuitesMultiTargetIntegrationTest extends AbstractIntegrationSpec {
    String jdkVersion

    def setup() {
        jdkVersion = AvailableJavaHomes.getAvailableJdk {
            // Include anything that isn't Java 8, and will still run Java 8 classes
            it.languageVersion.isJava9Compatible()
        }?.javaVersion?.majorVersion
        assumeNotNull(jdkVersion)
    }

    def "multiple targets can be used"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                        targets {
                            all {
                                testTask.configure {
                                    doLast {
                                        assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                                        assert classpath.size() == 2
                                        assert classpath.any { it.name == "junit-${DefaultJvmTestSuite.TestingFramework.JUNIT4.getDefaultVersion()}.jar" }
                                    }
                                }
                            }
                            test$jdkVersion {
                                testTask.configure {
                                    javaLauncher = javaToolchains.launcherFor {
                                        languageVersion = JavaLanguageVersion.of($jdkVersion)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        succeeds("check")

        then:
        result.assertTaskExecuted(":test")
        result.assertTaskExecuted(":test$jdkVersion")
    }

    // currently not supported, namespacing issues
    def "targets in two different test suites may not share names"() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                    similarToTest(JvmTestSuite) {
                        useJUnit()
                        // Add a target with overlapping name to the default `test` target
                        targets {
                            test {}
                        }
                    }
                }
            }
        """

        when:
        fails("check")

        then:
        failure.assertThatCause(containsNormalizedString("Cannot add task 'test' as a task with that name already exists."))
    }

    // currently not supported, variants are ambiguous without further information
    def "reports of multiple targets cannot be aggregated"() {
        buildFile << """
            plugins {
                id 'java'
                id 'test-report-aggregation'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                        targets {
                            test$jdkVersion {
                                testTask.configure {
                                    javaLauncher = javaToolchains.launcherFor {
                                        languageVersion = JavaLanguageVersion.of($jdkVersion)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        fails("testAggregateTestReport")

        then:
        failure.assertThatCause(containsNormalizedString("However we cannot choose between the following variants of project"))
    }

    def "reports of multiple targets can be aggregated if variant information is specified"() {
        buildFile << """
            plugins {
                id 'java'
                id 'test-report-aggregation'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                        targets {
                            test$jdkVersion {
                                testTask.configure {
                                    javaLauncher = javaToolchains.launcherFor {
                                        languageVersion = JavaLanguageVersion.of($jdkVersion)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            configurations {
                testResultsElementsForTest.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                }
                testResultsElementsForTest${jdkVersion}.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, $jdkVersion)
                }
            }
        """

        when:
        succeeds("testAggregateTestReport")

        then:
        result.assertTaskExecuted(":testAggregateTestReport")
    }
}
