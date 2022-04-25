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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportXmlFixture
import spock.lang.Issue

class JacocoTransitiveAggregationTest extends AbstractIntegrationSpec {
    def setup() {
        multiProjectBuild("root", ["direct", "transitiveTest"]) {
            buildFile.text = """
                apply plugin: 'jacoco-report-aggregation'

                repositories {
                    ${mavenCentralRepository()}
                }

                dependencies {
                    jacocoAggregation project(":direct")
                }

                reporting {
                    reports {
                        testCodeCoverageReport(JacocoCoverageReport) {
                            testType = TestSuiteType.UNIT_TEST
                        }
                    }
                }

                subprojects {
                    repositories {
                        ${mavenCentralRepository()}
                    }

                    plugins.withId('java') {
                        testing {
                            suites {
                                test {
                                    useJUnit()
                                }
                            }
                        }
                    }
                }
            """ + buildFile.text

            file("direct/build.gradle") << """
                plugins {
                    id 'java-library'
                    id 'jacoco'
                }

                dependencies {
                    testImplementation project(":transitiveTest")
                }
            """
            file("direct/src/main/java/direct/Multiplier.java").java """
                package direct;

                public class Multiplier {
                    int multiply(int x, int y) {
                        return x*y;
                    }
                }
            """
            file("direct/src/test/java/direct/MultiplierTest.java").java """
                package direct;

                import org.junit.Assert;
                import org.junit.Test;
                import transitiveTest.Powerize;

                public class MultiplierTest {
                    @Test
                    public void testMultiply() {
                        Multiplier multiplier = new Multiplier();
                        Assert.assertEquals(1, multiplier.multiply(1, 1));
                        Assert.assertEquals(4, multiplier.multiply(2, 2));
                        Assert.assertEquals(2, multiplier.multiply(1, 2));
                    }

                    @Test
                    public void testPowerize() {
                        Powerize powerizer = new Powerize();
                        Assert.assertEquals(4, powerizer.pow(2, 2));
                    }
                }
            """

            file("transitiveTest/build.gradle") << """
                plugins {
                    id 'java-library'
                }
            """
            file("transitiveTest/src/main/java/transitiveTest/Powerize.java").java """
                package transitiveTest;

                public class Powerize {
                    public int pow(int x, int y) {
                        return (int)Math.pow(x, y);
                    }
                }
            """
        }
    }

    @Issue("20532") // Not sure why this reproducer doesn't fail like the provided example.  Maybe Kotlin DSL vs. Groovy?
    def "can aggregate jacoco execution data from subprojects"() {
        given:

        when:
        succeeds(":testCodeCoverageReport")

        then:
        file("transitiveTest/build/jacoco/test.exec").assertDoesNotExist()
        file("direct/build/jacoco/test.exec").assertExists()

        file("build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()

        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("direct.Multiplier")
    }
}