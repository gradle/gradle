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
import org.gradle.api.plugins.jvm.internal.JUnit4TestToolchain
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm

import static org.junit.Assume.assumeNotNull

class TestSuitesMultiTargetIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {
    Jvm otherJvm

    def setup() {
        otherJvm = AvailableJavaHomes.differentVersion
        assumeNotNull(otherJvm)
    }

    private def setupBasicTestingProject(Iterable<String> extraPlugins = []) {
        file("src/test/java/MultiTargetTest.java") << """
            import org.junit.*;

            public class MultiTargetTest {
               @Test
               public void test() {
                  System.out.println("Tests running with " + System.getProperty("java.home"));
                  Assert.assertEquals(1, 1);
               }
            }
        """
        buildFile << """
            plugins {
                id 'java-library'
                ${extraPlugins.collect { "id '$it'" }.join('\n')}
            }

            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            ${mavenCentralRepository()}
        """
    }

    def "multiple targets can be used"() {
        setupBasicTestingProject()
        buildFile << """
            testing {
                suites {
                    test {
                        useJUnit()
                        targets {
                            all {
                                testTask.configure {
                                    doLast {
                                        assert testFramework instanceof ${JUnitTestFramework.canonicalName}
                                        // .collect() is intentional for a better error message on failure
                                        // The 6 elements are: junit, hamcrest, test classes and resources, main classes and resources
                                        assert classpath.collect().size() == 6
                                        assert classpath.any { it.name == "junit-${JUnit4TestToolchain.DEFAULT_VERSION}.jar" }
                                    }
                                }
                            }
                            testOtherJdk {
                                testTask.configure {
                                    javaLauncher = javaToolchains.launcherFor {
                                        languageVersion = JavaLanguageVersion.of(${otherJvm.javaVersion.majorVersion})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        withInstallations(Jvm.current(), otherJvm).succeeds("check")

        then:
        result.assertTaskExecuted(":test")
        result.assertTaskExecuted(":testOtherJdk")
    }

    // currently not supported, namespacing issues
    def "targets in two different test suites may not share names"() {
        setupBasicTestingProject()
        buildFile << """
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
        withInstallations(Jvm.current(), otherJvm).fails("check")

        then:
        failure.assertThatCause(containsNormalizedString("Cannot add task 'test' as a task with that name already exists."))
    }

    // currently not supported, variants are ambiguous without further information
    def "reports of multiple targets cannot be aggregated"() {
        setupBasicTestingProject(['test-report-aggregation'])
        buildFile << """
            testing {
                suites {
                    test {
                        useJUnit()
                        targets {
                            testOtherJdk {
                                testTask.configure {
                                    javaLauncher = javaToolchains.launcherFor {
                                        languageVersion = JavaLanguageVersion.of(${otherJvm.javaVersion.majorVersion})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        when:
        withInstallations(Jvm.current(), otherJvm).fails("testAggregateTestReport")

        then:
        failure.assertThatCause(containsNormalizedString("However we cannot choose between the following variants of project"))
    }

    def "reports of multiple targets can be aggregated if variant information is specified"() {
        setupBasicTestingProject(['test-report-aggregation'])
        buildFile << """
            testing {
                suites {
                    test {
                        useJUnit()
                        targets {
                            testOtherJdk {
                                testTask.configure {
                                    javaLauncher = javaToolchains.launcherFor {
                                        languageVersion = JavaLanguageVersion.of(${otherJvm.javaVersion.majorVersion})
                                    }
                                }
                            }
                        }
                    }
                }
            }

            configurations {
                testResultsElementsForTest.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, ${Jvm.current().javaVersion.majorVersion})
                }
                testResultsElementsForTestOtherJdk.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, ${otherJvm.javaVersion.majorVersion})
                }
            }
        """

        when:
        withInstallations(Jvm.current(), otherJvm).succeeds("testAggregateTestReport")

        then:
        result.assertTaskExecuted(":testAggregateTestReport")
    }
}
