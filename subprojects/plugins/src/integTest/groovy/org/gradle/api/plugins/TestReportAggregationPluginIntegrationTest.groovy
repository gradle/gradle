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

class TestReportAggregationPluginIntegrationTest extends AbstractIntegrationSpec {

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

    def 'can aggregate unit test results from subprojects'() {
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.test-report-aggregation'

//            logger.lifecycle configurations.testReportAggregation.files.join("\\n")

            //tasks.named('testAggregateTestReport').configure { dependsOn test } // FIXME this should not be necessary
//            def testAggregateTestReport = tasks.named('testAggregateTestReport') // FIXME this should not be necessary
//            testAggregateTestReport.configure {
//              dependsOn ':direct:test'
//              dependsOn ':transitive:test'
//            }
//            rootProject.subprojects.each {
//              testAggregateTestReport.configure { dependsOn tasks.named('test', Test) } //  tasks.named('test', Test).configure { testAggregateTestReport.configure { dependsOn it } }
//            }
        """
        when:
//        succeeds(":application:testCodeCoverageReport", "application:outgoingVariants", ":application:dependencies", ":transitive:outgoingVariants")
//        succeeds(":direct:test", ":transitive:test", ":application:test")
        succeeds(":application:testAggregateTestReport")
//        succeeds(":application:testAggregateTestReport", ":application:test", ":direct:test", ":transitive:test") // FIXME hack; do not explicitly invoke subprojects' test tasks
//        succeeds(":application:testAggregateTestReport", "application:outgoingVariants", ":transitive:outgoingVariants", "-i")
//        succeeds(":application:test")
//        succeeds(":direct:test")
//        succeeds(":transitive:test")

        then:
        result.assertTaskExecuted(":application:test")
        result.assertTaskExecuted(":direct:test")
        result.assertTaskExecuted(":transitive:test"
        )
        file("application/build/test-results/test/aggregated-results").assertIsDir()

        file("transitive/build/test-results/test/binary").assertIsDir()
        file("direct/build/test-results/test/binary").assertIsDir()
        file("application/build/test-results/test/binary").assertIsDir()

//        file("application/build/test-results/test/aggregated-results/index.html").assertExists()

        // single report dir contains all subproject test results
        file("application/build/test-results/test/aggregated-results/classes/application.AdderTest.html").assertExists()
        file("application/build/test-results/test/aggregated-results/classes/direct.MultiplierTest.html").assertExists()
        file("application/build/test-results/test/aggregated-results/classes/transitive.PowerizeTest.html").assertExists()

        file("application/build/test-results/test/aggregated-results/packages/application.html").assertExists()
        file("application/build/test-results/test/aggregated-results/packages/direct.html").assertExists()
        file("application/build/test-results/test/aggregated-results/packages/transitive.html").assertExists()

//        def report = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
//        report.assertHasClassCoverage("application.Adder")
//        report.assertHasClassCoverage("direct.Multiplier")
//        report.assertHasClassCoverage("transitive.Powerize")
    }
}
