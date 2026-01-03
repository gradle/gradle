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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.startsWith

class TestReportAggregationPluginIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT4
    }

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

    def 'can aggregate unit test results from dependent projects'() {
        given:
        file('application/build.gradle') << '''
            apply plugin: 'org.gradle.test-report-aggregation'
        '''

        when:
        succeeds(':application:testAggregateTestReport')

        then:
        result.assertTaskScheduled(":application:test")
        result.assertTaskScheduled(":direct:test")
        result.assertTaskScheduled(":transitive:test")
        result.assertTaskScheduled(":application:testAggregateTestReport")

        def transitiveResults = resultsFor(testDirectory.file('transitive'), 'tests/test')
        transitiveResults.assertAtLeastTestPathsExecuted('transitive.PowerizeTest')

        def directResults = resultsFor(testDirectory.file('direct'), 'tests/test')
        directResults.assertAtLeastTestPathsExecuted('direct.MultiplierTest')

        def applicationResults = resultsFor(testDirectory.file('application'), 'tests/test')
        applicationResults.assertAtLeastTestPathsExecuted('application.AdderTest')

        def aggregatedResults = aggregateResults(testDirectory.file('application'), 'tests/test', 'aggregated-results')
        aggregatedResults.assertAtLeastTestPathsExecuted("application.AdderTest", "direct.MultiplierTest", "transitive.PowerizeTest")
    }

    def 'multiple test suites create multiple aggregation tasks'() {
        given:
        file("transitive/build.gradle") << """
            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useJUnit()
                        dependencies {
                          implementation project()
                        }
                    }
                }
            }
        """
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.test-report-aggregation'

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
        succeeds(":application:testAggregateTestReport", ":application:integTestAggregateTestReport")

        then:
        result.assertTaskScheduled(":transitive:test")
        result.assertTaskScheduled(":direct:test")
        result.assertTaskScheduled(":application:test")
        result.assertTaskScheduled(":transitive:integTest")
        result.assertTaskScheduled(":application:integTest")
        result.assertTaskScheduled(":application:testAggregateTestReport")
        result.assertTaskScheduled(":application:integTestAggregateTestReport")

        def transitiveTestResults = resultsFor(testDirectory.file('transitive'), 'tests/test')
        transitiveTestResults.assertAtLeastTestPathsExecuted('transitive.PowerizeTest')

        def directTestResults = resultsFor(testDirectory.file('direct'), 'tests/test')
        directTestResults.assertAtLeastTestPathsExecuted('direct.MultiplierTest')

        def applicationTestResults = resultsFor(testDirectory.file('application'), 'tests/test')
        applicationTestResults.assertAtLeastTestPathsExecuted('application.AdderTest')

        def transitiveIntegTestResults = resultsFor(testDirectory.file('transitive'), 'tests/integTest')
        transitiveIntegTestResults.assertAtLeastTestPathsExecuted('transitive.ModTest')

        def applicationIntegTestResults = resultsFor(testDirectory.file('application'), 'tests/integTest')
        applicationIntegTestResults.assertAtLeastTestPathsExecuted('application.DivTest')

        def aggregatedTestResults = aggregateResults(testDirectory.file('application'), 'tests/test', 'aggregated-results')
        aggregatedTestResults.assertAtLeastTestPathsExecuted('application.AdderTest', 'direct.MultiplierTest', 'transitive.PowerizeTest')

        def aggregatedIntegTestResults = aggregateResults(testDirectory.file('application'), 'tests/integTest', 'aggregated-results')
        aggregatedIntegTestResults.assertAtLeastTestPathsExecuted('transitive.ModTest', 'application.DivTest')
    }

    def 'can aggregate tests from root project'() {
        given:
        buildFile << '''
            apply plugin: 'org.gradle.test-report-aggregation'

            dependencies {
                testReportAggregation project(":application")
                testReportAggregation project(":direct")
            }

            reporting {
                reports {
                    testAggregateTestReport(AggregateTestReport) {
                        testSuiteName = "test"
                    }
                }
            }
        '''

        when:
        succeeds(':testAggregateTestReport')

        then:
        def transitiveTestResults = new HtmlTestExecutionResult(testDirectory.file('transitive'))
        transitiveTestResults.assertTestClassesExecuted('transitive.PowerizeTest')

        def directTestResults = new HtmlTestExecutionResult(testDirectory.file('direct'))
        directTestResults.assertTestClassesExecuted('direct.MultiplierTest')

        def applicationTestResults = new HtmlTestExecutionResult(testDirectory.file('application'))
        applicationTestResults.assertTestClassesExecuted('application.AdderTest')

        def aggregatedTestResults = new HtmlTestExecutionResult(testDirectory, 'build/reports/tests/test/aggregated-results')
        aggregatedTestResults.assertTestClassesExecuted('application.AdderTest', 'direct.MultiplierTest', 'transitive.PowerizeTest')
    }


    def 'can aggregate tests from root project with different overall statuses'() {
        given:
        buildFile << '''
            apply plugin: 'org.gradle.test-report-aggregation'

            dependencies {
                testReportAggregation project(":application")
                testReportAggregation project(":direct")
            }

            reporting {
                reports {
                    testAggregateTestReport(AggregateTestReport) {
                        testSuiteName = "test"
                    }
                }
            }
        '''

        file("transitive/src/test/java/transitive/PowerizeTest.java").java """
                package transitive;

                import org.junit.Assert;
                import org.junit.Ignore;
                import org.junit.Test;

                @Ignore
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
        file("direct/src/test/java/direct/MultiplierTest.java").java """
                package direct;

                import org.junit.Assert;
                import org.junit.Test;

                public class MultiplierTest {
                    @Test
                    public void testMultiply() {
                        Multiplier multiplier = new Multiplier();
                        Assert.assertEquals(-1, multiplier.multiply(1, 1));
                        Assert.assertEquals(0, multiplier.multiply(2, 2));
                        Assert.assertEquals(1, multiplier.multiply(1, 2));
                    }
                }
            """

        when:
        fails(':testAggregateTestReport', "--continue")

        then:
        def transitiveTestResults = new HtmlTestExecutionResult(testDirectory.file('transitive'))
        transitiveTestResults.assertTestClassesExecuted('transitive.PowerizeTest')

        def directTestResults = new HtmlTestExecutionResult(testDirectory.file('direct'))
        directTestResults.assertTestClassesExecuted('direct.MultiplierTest')

        def applicationTestResults = new HtmlTestExecutionResult(testDirectory.file('application'))
        applicationTestResults.assertTestClassesExecuted('application.AdderTest')

        def aggregatedTestResults = new HtmlTestExecutionResult(testDirectory, 'build/reports/tests/test/aggregated-results')
        aggregatedTestResults.assertTestClassesExecuted('application.AdderTest', 'direct.MultiplierTest', 'transitive.PowerizeTest')

        aggregatedTestResults.assertHtml(".successGroup") {e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "a"
                e[0].text() == "Gradle Test Run :application:test"
            }
        }
        aggregatedTestResults.assertHtml(".failureGroup") {e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "a"
                e[0].text() == "Gradle Test Run :direct:test"
            }
        }
        aggregatedTestResults.assertHtml(".skippedGroup") {e ->
            verifyAll {
                e.size() == 1
                e[0].tagName() == "a"
                e[0].text() == "Gradle Test Run :transitive:test"
            }
        }
    }

    def 'can aggregate tests from root project when subproject does not have tests'() {
        given:
        buildFile << '''
            apply plugin: 'org.gradle.test-report-aggregation'

            dependencies {
                testReportAggregation project(":application")
                testReportAggregation project(":direct")
            }

            reporting {
                reports {
                    testAggregateTestReport(AggregateTestReport) {
                        testSuiteName = "test"
                    }
                }
            }
        '''
        // remove tests from transitive
        file("transitive/src/test").deleteDir()

        when:
        succeeds(':testAggregateTestReport')

        then:
        def aggregatedTestResults = aggregateResults(testDirectory, 'tests/test', 'aggregated-results')
        aggregatedTestResults.assertAtLeastTestPathsExecuted('application.AdderTest', 'direct.MultiplierTest')
    }

    def 'test verification failure prevents creation of aggregated report'() {
        given:file("application/build.gradle") << """
            apply plugin: 'org.gradle.test-report-aggregation'
        """
        file("direct/src/test/java/direct/MultiplierTest.java").java """
                package direct;

                import org.junit.Assert;
                import org.junit.Test;

                public class MultiplierTest {
                    @Test
                    public void testMultiply() {
                        Assert.fail("intentional failure");
                    }
                }
            """

        when:
        fails(":application:testAggregateTestReport")

        then:
        failure.assertHasDescription("Execution failed for task ':direct:test' (registered by plugin 'org.gradle.jvm-test-suite').")
               .assertThatCause(startsWith("There were failing tests"))
        result.assertTasksNotScheduled(':application:testAggregateTestReport')

        file("application/build/reports/tests/test/aggregated-results").assertDoesNotExist()
    }

    def 'test verification failure creates aggregated report with --continue flag'() {
        given:file("application/build.gradle") << """
            apply plugin: 'org.gradle.test-report-aggregation'
        """
        file("direct/src/test/java/direct/MultiplierTest.java").java """
                package direct;

                import org.junit.Assert;
                import org.junit.Test;

                public class MultiplierTest {
                    @Test
                    public void testMultiply() {
                        Assert.fail("intentional failure");
                    }
                }
            """

        when:
        fails(":application:testAggregateTestReport", "--continue")

        then:
        result.assertTaskScheduled(":application:test")
        result.assertTaskScheduled(":direct:test")
        result.assertTaskScheduled(":transitive:test")
        result.assertTaskScheduled(":application:testAggregateTestReport")

        def transitiveTestResults = resultsFor(testDirectory.file('transitive'))
        transitiveTestResults.assertAtLeastTestPathsExecuted('transitive.PowerizeTest')

        def directTestResults = resultsFor(testDirectory.file('direct'))
        directTestResults.assertAtLeastTestPathsExecuted('direct.MultiplierTest')

        def applicationTestResults = resultsFor(testDirectory.file('application'))
        applicationTestResults.assertAtLeastTestPathsExecuted('application.AdderTest')

        def aggregatedResults = aggregateResults(testDirectory.file("application"), "tests/test", "aggregated-results")
        aggregatedResults.assertAtLeastTestPathsExecuted("application.AdderTest", "direct.MultiplierTest", "transitive.PowerizeTest")
    }

    def 'test aggregated report can be put into a custom location'() {
        given:
        // Reordering the plugins so that Java is applied later
        file("application/build.gradle").text = """
                plugins {
                    id 'org.gradle.test-report-aggregation'
                    id 'java'
                }

                java {
                    testReportDir = layout.buildDirectory.dir("non-default-location")
                }
                dependencies {
                    implementation project(":direct")
                }
            """

        when:
        succeeds(":application:testAggregateTestReport", "--continue")

        then:
        result.assertTaskScheduled(":application:testAggregateTestReport")

        def aggregatedResults = new HtmlTestExecutionResult(testDirectory, "application/build/non-default-location/test/aggregated-results")
        aggregatedResults.assertTestClassesExecuted("application.AdderTest", "direct.MultiplierTest", "transitive.PowerizeTest")
    }

    def 'catastrophic failure of single test prevents creation of aggregated report'() {
        given:
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.test-report-aggregation'
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
        fails(":application:testAggregateTestReport", "--continue")

        then:
        // despite --continue flag, :application:testAggregateTestReport will not execute due to catastrophic failure in :direct:test
        result.assertTaskScheduled(":application:test")
        result.assertTaskScheduled(":direct:test")
        result.assertTaskScheduled(":transitive:test")
        result.assertTasksNotScheduled(":application:testAggregateTestReport")

        file("application/build/reports/tests/test/aggregated-results").assertDoesNotExist()

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
                                    jvmArgs('-XX:UnknownArgument')
                                }
                            }
                        }
                    }
                }
        '''
        file("application/build.gradle") << """
            apply plugin: 'org.gradle.test-report-aggregation'
        """

        when:
        fails(":application:testAggregateTestReport", "--continue")

        then:
        // despite --continue flag, :application:testAggregateTestReport will not execute due to catastrophic failures
        result.assertTasksNotScheduled(":application:test")
        result.assertTasksNotScheduled(":direct:test")
        result.assertTasksNotScheduled(":transitive:test")
        result.assertTasksNotScheduled(":application:testAggregateTestReport")

        file("application/build/reports/tests/test/aggregated-results").assertDoesNotExist()
    }

    @Issue("https://github.com/gradle/gradle/issues/29820")
    def "can aggregate when a jar file dependency is used"() {
        buildFile("application/build.gradle", """
            apply plugin: 'org.gradle.test-report-aggregation'

            dependencies {
                // BUG: Local file dependencies are breaking the test report aggregation plugin!
                implementation(files("bug.jar"))
            }
        """)

        expect:
        succeeds ":application:testAggregateTestReport"
    }
}
