/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing.junit.junit6.jupiter

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.junit.AbstractJUnitIntegrationTest
import org.gradle.testing.junit.jupiter.JUnitJupiterMultiVersionTest

@TargetCoverage({ JUnitCoverage.JUNIT_6 })
class JUnit6JUnitIntegrationTest extends AbstractJUnitIntegrationTest implements JUnitJupiterMultiVersionTest, JavaToolchainFixture {
    def "works with JUnit 6 features (MethodOrderer.Default and ClassOrderer.Default)"() {
        given:
        buildFile("""
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter("${version}")

                targets.all {
                    testTask.configure {
                        testLogging {
                            showStandardStreams = true
                        }
                    }
                }
            }
        """)

        file('src/test/java/org/gradle/JUnit6OrderingTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.TestMethodOrder;
            import org.junit.jupiter.api.MethodOrderer;
            import org.junit.jupiter.api.Order;

            @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
            public class JUnit6OrderingTest {
                @Test
                @Order(2)
                public void testB() {
                    // Runs second in outer class
                    System.out.println("testB");
                }

                @Test
                @Order(1)
                public void testA() {
                    // Runs first in outer class
                    System.out.println("testA");
                }

                @Nested
                @TestMethodOrder(MethodOrderer.Default.class)
                class NestedTestWithDefaultOrdering {
                    @Test
                    public void testZ() {
                        // Uses default ordering (not parent's OrderAnnotation)
                        System.out.println("testZ");
                    }

                    @Test
                    public void testY() {
                        // Uses default ordering (not parent's OrderAnnotation)
                        System.out.println("testY");
                    }
                }
            }
        '''

        when:
        succeeds('test')

        then:
        def results = resultsFor(testDirectory)
        results.testPathPreNormalized(':org.gradle.JUnit6OrderingTest').onlyRoot().assertChildCount(3, 0)
        results.testPathPreNormalized(':org.gradle.JUnit6OrderingTest:org.gradle.JUnit6OrderingTest$NestedTestWithDefaultOrdering').onlyRoot().assertChildCount(2, 0)

        and: "tests are run in the proper order"
        outputContains("""JUnit6OrderingTest > testA() STANDARD_OUT
    testA

JUnit6OrderingTest > testB() STANDARD_OUT
    testB

JUnit6OrderingTest > NestedTestWithDefaultOrdering > testY() STANDARD_OUT
    testY

JUnit6OrderingTest > NestedTestWithDefaultOrdering > testZ() STANDARD_OUT
    testZ""")
    }

    def "Gradle emits resolution help message if JUnit6 is used with Java below 17"() {
        def jdk = AvailableJavaHomes.getJdk8()
        buildFile"""
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}
            ${javaPluginToolchainVersion(jdk)}

            testing.suites.test {
                useJUnitJupiter("${version}")
            }
        """
        file("src/test/java/com/example/FooTest.java").java """
package com.example;

import org.junit.jupiter.api.Test;

public class FooTest {
    @Test
    public void testIt() {
        assert true;
    }
}
        """
        when:
        withInstallations(jdk)
        fails("test")
        then:
        failure.assertHasCause("Dependency resolution is looking for a library compatible with JVM runtime version 8, but 'org.junit.jupiter:junit-jupiter:${version}' is only compatible with JVM runtime version 17 or newer.")
    }

    def "Gradle emits help message if JUnit6 is used with Java below 17"() {
        buildFile"""
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}
            ${javaPluginToolchainVersion(17)}

            testing.suites.test {
                useJUnitJupiter("${version}")
                targets {
                    all {
                        testTask.configure {
                            javaLauncher = javaToolchains.launcherFor {
                                languageVersion = JavaLanguageVersion.of(8)
                            }
                        }
                    }
                }
            }
        """
        file("src/test/java/com/example/FooTest.java").java """
package com.example;

import org.junit.jupiter.api.Test;

public class FooTest {
    @Test
    public void testIt() {
        assert true;
    }
}
        """
        when:
        withInstallations(AvailableJavaHomes.getJdk8(), AvailableJavaHomes.getJdk17())
        fails("test")
        then:
        // TODO: This captures existing behavior, but not desired behavior
        failure.assertHasCause("Test process encountered an unexpected problem.")
    }
}
