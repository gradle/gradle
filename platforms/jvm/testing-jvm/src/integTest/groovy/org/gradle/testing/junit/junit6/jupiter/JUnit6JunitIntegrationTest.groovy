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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec;
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.junit.AbstractJUnitIntegrationTest
import org.gradle.testing.junit.jupiter.JUnitJupiterMultiVersionTest

@TargetCoverage({ JUnitCoverage.JUNIT_6 })
class JUnit6JunitIntegrationTest extends AbstractJUnitIntegrationTest implements JUnitJupiterMultiVersionTest {

    def "works with JUnit 6 features (MethodOrderer.Default and ClassOrderer.Default)"() {
        given:
        buildFile("""
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${MultiVersionIntegrationSpec.version}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }

            test {
                useJUnitPlatform()

                testLogging {
                    showStandardStreams = true
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
}
