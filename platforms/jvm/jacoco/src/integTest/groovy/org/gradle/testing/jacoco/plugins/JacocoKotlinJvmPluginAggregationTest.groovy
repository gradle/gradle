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
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportXmlFixture
import spock.lang.Issue

/**
 * This test class exists to verify a specific issue - if a project uses the `kotlin-jvm` plugin
 * and has no actual tests but is still included on the jacoco aggregation classpath transitively,
 * it will cause the report aggregation to fail.
 *
 * This is due to that plugin adding attributes to the legacy `default` variant which cause it to match
 * the variant selection performed by the plugin when looking for aggregation data.  Without the fix for this issue (check file signature of
 * all files to be added to the jacoco report to ensure none are zip), this test will fail.
 */
@Issue("https://github.com/gradle/gradle/issues/20532")
class JacocoKotlinJvmPluginAggregationTest extends AbstractIntegrationSpec {

    def kotlinVersion = new KotlinGradlePluginVersions().latestStableOrRC

    def setup() {
        multiProjectBuild("root", ["direct", "transitive"]) {
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
                    testImplementation project(":transitive")
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
                import transitive.Powerize;

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

            file("transitive/build.gradle") << """
                plugins {
                    id 'java-library'
                    id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'
                }
            """
            file("transitive/src/main/java/transitive/Powerize.java").java """
                package transitive;

                public class Powerize {
                    public int pow(int x, int y) {
                        return (int)Math.pow(x, y);
                    }
                }
            """
        }
    }

    def "can aggregate jacoco execution data from a subproject with kotlin-dsl and no tests"() {
        when:
        succeeds(":testCodeCoverageReport")

        then:
        file("transitive/build/jacoco/test.exec").assertDoesNotExist()
        file("transitive/build/libs/transitive-1.0.jar").assertExists()
        file("direct/build/jacoco/test.exec").assertExists()

        file("build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()

        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("direct.Multiplier")
    }
}
