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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportXmlFixture

class JacocoAggregationIntegrationTest extends AbstractIntegrationSpec {
    def "can aggregate jacoco execution data from subprojects"() {
        multiProjectBuild("root", ["application", "direct", "transitive"]) {
            buildFile << """
                subprojects {
                    repositories {
                        ${mavenCentralRepository()}
                    }

                    plugins.withId("jvm-test-suite") {
                        testing {
                            suites {
                                test {
                                    dependencies {
                                        implementation "junit:junit:4.13"
                                    }
                                }
                            }
                        }
                    }
                }
            """
            file("application/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
                    id 'org.gradle.jacoco-report-aggregation'
                }

                dependencies {
                    implementation project(":direct")
                }
            """

            file("application/src/main/java/application/Adder.java").java """
                package application;

                public class Adder {
                    int add(int x, int y) {
                        return x+y;
                    }
                }
            """
            file("application/src/test/java/application/AdderTest.java").java """
                package application;

                import org.junit.Assert;
                import org.junit.Test;

                public class AdderTest {
                    @Test
                    public void testAdd() {
                        Adder adder = new Adder();
                        Assert.assertEquals(2, adder.add(1, 1));
                        Assert.assertEquals(4, adder.add(2, 2));
                        Assert.assertEquals(3, adder.add(1, 2));
                    }
                }
            """

            file("direct/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
                }

                dependencies {
                    implementation project(":transitive")
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
                
                public class MultiplierTest {
                    @Test
                    public void testMultiply() {
                        Multiplier multiplier = new Multiplier();
                        Assert.assertEquals(1, multiplier.multiply(1, 1));
                        Assert.assertEquals(4, multiplier.multiply(2, 2));
                        Assert.assertEquals(2, multiplier.multiply(1, 2));
                    }
                }
            """
            file("transitive/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
                }
            """
            file("transitive/src/main/java/transitive/Powerize.java").java """
                package transitive;

                public class Powerize {
                    int pow(int x, int y) {
                        return (int)Math.pow(x, y);
                    }
                }
            """
            file("transitive/src/test/java/transitive/PowerizeTest.java").java """
                package transitive;

                import org.junit.Assert;
                import org.junit.Test;
                
                public class PowerizeTest {
                    @Test
                    public void testPow() {
                        Powerize powerize = new Powerize();
                        Assert.assertEquals(1, powerize.pow(1, 1));
                        Assert.assertEquals(4, powerize.pow(2, 2));
                        Assert.assertEquals(1, powerize.pow(1, 2));
                    }
                }
            """
        }
        when:
        succeeds(":application:testCodeCoverageReport")
        then:
        file("transitive/build/jacoco/test.exec").assertExists()
        file("direct/build/jacoco/test.exec").assertExists()
        file("application/build/jacoco/test.exec").assertExists()

        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()

        def report = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("application.Adder")
        report.assertHasClassCoverage("direct.Multiplier")
        report.assertHasClassCoverage("transitive.Powerize")
    }
}
