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

import static org.hamcrest.CoreMatchers.startsWith

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

    def "can aggregate jacoco execution data from dependent projects"() {
        given:
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'
        """

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

    def "aggregated report does not contain external dependencies"() {
        given:
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

    def "aggregated report resolves sources variant of project dependencies"() {
        given:
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            reporting.reports.testCodeCoverageReport.reportTask.configure {
                doLast {
                    assert it.sourceDirectories.files.size() == 6
                    println it.sourceDirectories.files
                }
            }
        """
        file("transitive/build.gradle") << """
            dependencies {
                implementation 'org.apache.commons:commons-io:1.3.2'
            }
        """

        when:
        succeeds(':application:testCodeCoverageReport')

        then:
        outputContains(file('application/src/main/java').absolutePath)
        outputContains(file('application/src/main/resources').absolutePath)
        outputContains(file('direct/src/main/java').absolutePath)
        outputContains(file('direct/src/main/resources').absolutePath)
        outputContains(file('transitive/src/main/java').absolutePath)
        outputContains(file('transitive/src/main/resources').absolutePath)
    }

    def "aggregated report resolves classes variant of project dependencies"() {
        given:
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            reporting.reports.testCodeCoverageReport.reportTask.configure {
                doLast {
                    assert it.classDirectories.files.size() == 3
                    println it.classDirectories.files
                }
            }
        """
        file("transitive/build.gradle") << """
            dependencies {
                implementation 'org.apache.commons:commons-io:1.3.2'
            }
        """

        when:
        succeeds(':application:testCodeCoverageReport')

        then:
        outputContains(file('application/build/classes/java/main').absolutePath)
        outputDoesNotContain(file('application/build/libs/application-1.0.jar').absolutePath)
        outputContains(file('direct/build/classes/java/main').absolutePath)
        outputDoesNotContain(file('direct/build/libs/direct-1.0.jar').absolutePath)
        outputContains(file('transitive/build/classes/java/main').absolutePath)
        outputDoesNotContain(file('transitive/build/libs/transitive-1.0.jar').absolutePath)
    }

    def 'aggregated report infers dependency versions from platform'() {
        given:
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'
        """
        file("transitive/build.gradle") << """
            dependencies {
                implementation 'org.apache.commons:commons-io:1.3.2'
                implementation(platform('org.springframework.boot:spring-boot-dependencies:2.5.8'))
                runtimeOnly 'org.codehaus.janino:janino'
            }
        """

        when:
        succeeds(":application:testCodeCoverageReport")

        then:
        def report = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertDoesNotContainClass("org.apache.commons.io.IOUtils")
        report.assertDoesNotContainClass("org.codehaus.janino.Parser")
    }

    def 'multiple test suites create multiple aggregation tasks'() {
        given:
        file("transitive/build.gradle") << """
            testing {
                suites {
                    integTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TEST
                        useJUnit()
                        dependencies {
                          implementation project()
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
                        testType = TestSuiteType.INTEGRATION_TEST
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
        succeeds(":application:testCodeCoverageReport", ":application:integTestCodeCoverageReport")

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
        testReport.assertHasClassButNoCoverage("transitive.Divisor")

        def integTestReport = new JacocoReportXmlFixture(file("application/build/reports/jacoco/integTestCodeCoverageReport/integTestCodeCoverageReport.xml"))
        integTestReport.assertHasClassButNoCoverage("application.Adder")
        integTestReport.assertHasClassButNoCoverage("direct.Multiplier")
        integTestReport.assertHasClassButNoCoverage("transitive.Powerize")
        integTestReport.assertHasClassCoverage("transitive.Divisor")
    }

    def "can aggregate jacoco reports from root project"() {
        given:
        buildFile << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            dependencies {
                jacocoAggregation project(":application")
                jacocoAggregation project(":direct")
            }

            reporting {
                reports {
                    testCodeCoverageReport(JacocoCoverageReport) {
                        testType = TestSuiteType.UNIT_TEST
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

    def "can aggregate jacoco reports from root project when subproject doesn't have tests"() {
        given:
        buildFile << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            dependencies {
                jacocoAggregation project(":application")
                jacocoAggregation project(":direct")
            }

            reporting {
                reports {
                    testCodeCoverageReport(JacocoCoverageReport) {
                        testType = TestSuiteType.UNIT_TEST
                    }
                }
            }
        """

        // remove tests from transitive
        file("transitive/src/test").deleteDir()

        when:
        succeeds(":testCodeCoverageReport")

        then:
        file("transitive/build/jacoco/test.exec").assertDoesNotExist()
        file("direct/build/jacoco/test.exec").assertExists()
        file("application/build/jacoco/test.exec").assertExists()

        file("build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()

        def report = new JacocoReportXmlFixture(file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("application.Adder")
        report.assertHasClassCoverage("direct.Multiplier")
        report.assertHasClassButNoCoverage("transitive.Powerize")
    }

    def "can aggregate jacoco reports from root project with platform"() {
        given:
        file("transitive/build.gradle") << """
            dependencies {
                implementation 'org.apache.commons:commons-io:1.3.2'
                implementation(platform('org.springframework.boot:spring-boot-dependencies:2.5.8'))
                runtimeOnly 'org.codehaus.janino:janino'
            }
        """

        buildFile << """
            apply plugin: 'jvm-ecosystem'
            apply plugin: 'org.gradle.jacoco-report-aggregation'

            dependencies {
                jacocoAggregation project(":application")
                jacocoAggregation project(":direct")
            }

            reporting {
                reports {
                    testCodeCoverageReport(JacocoCoverageReport) {
                        testType = TestSuiteType.UNIT_TEST
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

        // verify that external dependencies are filtered
        report.assertDoesNotContainClass("org.apache.commons.io.IOUtils")
        report.assertDoesNotContainClass("org.codehaus.janino.Parser")
    }

    def 'test verification failure prevents creation of aggregated report'() {
        given:
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
        fails(":application:testCodeCoverageReport")

        then:
        failure.assertHasDescription("Execution failed for task ':direct:test'.")
            .assertThatCause(startsWith("There were failing tests"))
        result.assertTaskNotExecuted(':application:testCodeCoverageReport"')

        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertDoesNotExist()
        file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml").assertDoesNotExist()
    }

    def 'test verification failure creates aggregated report with --continue flag'() {
        given:
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
                    }
                }
            """

        when:
        def result = fails(":application:testCodeCoverageReport", "--continue")

        then:
        result.assertTaskExecuted(':direct:test')
        result.assertTaskExecuted(':transitive:test')
        result.assertTaskExecuted(':application:test')
        result.assertTaskExecuted(':application:testCodeCoverageReport')

        file("direct/build/jacoco/test.exec").assertExists()
        file("transitive/build/jacoco/test.exec").assertExists()
        file("application/build/jacoco/test.exec").assertExists()

        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertExists()
        file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml").assertExists()

        def report = new JacocoReportXmlFixture(file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"))
        report.assertHasClassCoverage("application.Adder")
        report.assertHasClassButNoCoverage("direct.Multiplier") // direct will _not_ have coverage as its test task has a verification failure
        report.assertHasClassCoverage("transitive.Powerize")
    }

    def 'catastrophic failure of single test prevents creation of aggregated report'() {
        given:
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
                        System.exit(42); // prematurely exit the testing VM
                    }
                }
            """

        when:
        executer.withStackTraceChecksDisabled()
        def result = fails(":application:testCodeCoverageReport", "--continue")

        then:
        result.assertTaskExecuted(':direct:test')
        result.assertTaskExecuted(':transitive:test')
        result.assertTaskExecuted(':application:test')
        result.assertTaskNotExecuted(':application:testCodeCoverageReport')

        file("direct/build/jacoco/test.exec").assertExists()
        file("transitive/build/jacoco/test.exec").assertExists()
        file("application/build/jacoco/test.exec").assertExists()

        // despite --continue flag, :application:testCodeCoverageReport will not execute due to catastrophic failure in :direct:test
        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertDoesNotExist()
        file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml").assertDoesNotExist()
    }

    def 'catastrophic failure of every test task prevents creation of aggregated report'() {
        given:
        // prevent all test VMs from starting
        buildFile << '''
                subprojects {
                    plugins.withId('java') {
                        testing {
                            suites {
                                test {
                                    useJUnit()
                                    jvmArgs '-XX:UnknownArgument'
                                }
                            }
                        }
                    }
                }

        '''
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.jacoco-report-aggregation'
        """

        when:
        def result = fails(":application:testCodeCoverageReport", "--continue")

        then:
        result.assertTaskNotExecuted(':direct:test')
        result.assertTaskNotExecuted(':transitive:test')
        result.assertTaskNotExecuted(':application:test')
        result.assertTaskNotExecuted(':application:testCodeCoverageReport')

        file("direct/build/jacoco/test.exec").assertDoesNotExist()
        file("transitive/build/jacoco/test.exec").assertDoesNotExist()
        file("application/build/jacoco/test.exec").assertDoesNotExist()

        // despite --continue flag, :application:testCodeCoverageReport will not execute due to catastrophic failures
        file("application/build/reports/jacoco/testCodeCoverageReport/html/index.html").assertDoesNotExist()
        file("application/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml").assertDoesNotExist()
    }

}
