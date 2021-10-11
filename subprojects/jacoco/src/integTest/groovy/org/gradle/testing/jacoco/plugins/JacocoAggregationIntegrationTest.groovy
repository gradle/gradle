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
    def setup() {
        multiProjectBuild("root", ["application", "direct", "transitive"]) {
            buildFile << """
                allprojects {
                    repositories {
                        ${mavenCentralRepository()}
                    }
                }
                subprojects {
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
            """
            file("application/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
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
    }

    def "can aggregate jacoco execution data from subprojects"() {
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'
        """
        when:
        succeeds(":application:testCodeCoverageReport", "application:outgoingVariants", ":application:dependencies", ":transitive:outgoingVariants")
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

    def "aggregated report does not contain external dependencies"() {
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'
        """
        file("transitive/build.gradle") << """
            dependencies {
                implementation 'org.apache.commons:commons-io:1.3.2'
            }
        """
        when:
        succeeds(":application:testCodeCoverageReport")
        then:
        def report = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertDoesNotContainClass("org.apache.commons.io.IOUtils")
    }

    def 'multiple test suites create multiple aggregation tasks'() {
        given:
        file("transitive/build.gradle") << """
            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        dependencies {
                          implementation project
                        }
                    }
                }
            }
        """
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        dependencies {
                            implementation project(':transitive') // necessary to access Divisor when compiling test
                        }
                    }
                }
            }
        """

        file("transitive/src/main/java/transitive/Divisor.java").java """
                package transitive;

                public class Divisor {
                    public int div(int x, int y) {
                        return x / y;
                    }
                    public int mod(int x, int y) {
                        return x % y;
                    }
                }
            """

        file("application/src/integTest/java/application/DivTest.java").java """
                package application;

                import org.junit.Assert;
                import org.junit.Test;
                import transitive.Divisor;

                public class DivTest {
                    @Test
                    public void testDiv() {
                        Divisor divisor = new Divisor();
                        Assert.assertEquals(2, divisor.div(4, 2));
                    }
                }
            """
        file("transitive/src/integTest/java/transitive/ModTest.java").java """
                package transitive;

                import org.junit.Assert;
                import org.junit.Test;

                public class ModTest {
                    @Test
                    public void testMod() {
                        Divisor divisor = new Divisor();
                        Assert.assertEquals(1, divisor.mod(5, 2));
                    }
                }
            """

        when:
        succeeds(":application:testCodeCoverageReport", ":application:integTestCodeCoverageReport", ":application:dependencies", ":transitive:outgoingVariants")

        then:
        file("transitive/build/jacoco/test.exec").assertExists()
        file("transitive/build/jacoco/integTest.exec").assertExists()
        file("direct/build/jacoco/test.exec").assertExists()
        file("direct/build/jacoco/integTest.exec").assertDoesNotExist()
        file("application/build/jacoco/test.exec").assertExists()
        file("application/build/jacoco/integTest.exec").assertExists()

        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()
        file("application/build/reports/jacoco/integTestCodeCoverageReport/html/index.html").assertExists()

        def testReport = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        testReport.assertHasClassCoverage("application.Adder")
        testReport.assertHasClassCoverage("direct.Multiplier")
        testReport.assertHasClassCoverage("transitive.Powerize")
        testReport.assertHasClassCoverage("transitive.Divisor", 0)

        def integTestReport = new JacocoReportXmlFixture(file("application/build/reports/jacoco/integTestCodeCoverageReport/integTestCodeCoverageReport.xml"))
        integTestReport.assertHasClassCoverage("application.Adder", 0)
        integTestReport.assertHasClassCoverage("direct.Multiplier", 0)
        integTestReport.assertHasClassCoverage("transitive.Powerize", 0)
        integTestReport.assertHasClassCoverage("transitive.Divisor")
    }

    def "can aggregate jacoco reports from root project"() {
        buildFile << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            dependencies {
                jacocoAggregation project(":application")
                jacocoAggregation project(":direct")
                jacocoAggregation project(":transitive")
            }

            reporting {
                reports {
                    testCodeCoverageReport(JacocoCoverageReport) {
                        executionData.from(JacocoReportAggregationPlugin.resolvableJacocoData(configurations.jacocoAggregation, objects, "test"))
                    }
                }
            }
        """
        when:
        succeeds(":testCodeCoverageReport")
        then:
        file("transitive/build/jacoco/test.exec").assertExists()
        file("direct/build/jacoco/test.exec").assertExists()
        file("application/build/jacoco/test.exec").assertExists()

        file("build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()

        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("application.Adder")
        report.assertHasClassCoverage("direct.Multiplier")
        report.assertHasClassCoverage("transitive.Powerize")
    }

    // TODO
    //  verification failure, first use
    //  mechanical failure, first use
    //  verification failure with small change after successful run
    //  mechanical failure with small change after successful run

    def 'verification failure on first-use continues creation of aggregated report, but with sparse coverage results'() {
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'
        """
        file("direct/src/test/java/direct/MultiplierTest.java").java """
                package direct;

                import org.junit.Assert;
                import org.junit.Test;

                public class MultiplierTest {
                    @Test
                    public void testMultiply() {
                        Assert.fail("intentional failure to test jacoco coverage figures");
                        Multiplier multiplier = new Multiplier();
                        Assert.assertEquals(1, multiplier.multiply(1, 1));
                        Assert.assertEquals(4, multiplier.multiply(2, 2));
                        Assert.assertEquals(2, multiplier.multiply(1, 2));
                    }
                }
            """
        when:
        fails(":application:testCodeCoverageReport", "application:outgoingVariants", ":application:dependencies", ":transitive:outgoingVariants")

        then:
        file("direct/build/jacoco/test.exec").assertExists()
        //file("transitive/build/jacoco/test.exec").assertExists() // FIXME expected failure; as the verification failure in :direct:test causes :transitive:test to be pruned from the execution graph
        file("application/build/jacoco/test.exec").assertExists()

        // TODO make :application:testCodeCoverageReport execute even though :direct:test contains a verification failure
        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()

        def report = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("application.Adder")
        report.assertHasClassCoverage("direct.Multiplier", 0) // direct will _not_ have coverage as its test task has a verification failure
        report.assertHasClassCoverage("transitive.Powerize")
    }

    def 'mechanical failure on first-use prevents creation of aggregated report'() {

    }

}
