/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.integtests.fixtures.JUnitTestClassExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL
import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo

class TestReportTaskIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    Sample sample = new Sample(temporaryFolder)

    @UsesSample("testing/testReport/groovy")
    def "can generate report for subprojects"() {
        given:
        super.sample(sample)

        when:
        run "testReport"

        then:
        def htmlReport = new HtmlTestExecutionResult(sample.dir, "build/reports/allTests")
        htmlReport.testClass("org.gradle.sample.CoreTest").assertTestCount(1, 0, 0).assertTestPassed("ok").assertStdout(equalTo("hello from CoreTest.\n"))
        htmlReport.testClass("org.gradle.sample.UtilTest").assertTestCount(1, 0, 0).assertTestPassed("ok").assertStdout(equalTo("hello from UtilTest.\n"))
    }

    def "merges report with duplicated classes and methods"() {
        given:
        buildFile << """
            $junitSetup
            def test = tasks.named('test', Test)
            test.configure {
                ignoreFailures = true
                useJUnit {
                    excludeCategories 'org.gradle.testing.SuperClassTests'
                    excludeCategories 'org.gradle.testing.SubClassTests'
                }
            }

            testing {
                suites {
                    superTest(JvmTestSuite) {
                        useJUnit()
                        sources.java.srcDirs(testing.suites.test.sources.allJava.srcDirs)
                        targets.all {
                            testTask.configure {
                                ignoreFailures = true
                                systemProperty('category', 'super')
                                testFramework {
                                    includeCategories 'org.gradle.testing.SuperClassTests'
                                }
                            }
                        }
                    }
                    subTest(JvmTestSuite) {
                        useJUnit()
                        sources.java.srcDirs(testing.suites.test.sources.allJava.srcDirs)
                        targets.all {
                            testTask.configure {
                                ignoreFailures = true
                                systemProperty('category', 'sub')
                                testFramework {
                                    includeCategories 'org.gradle.testing.SubClassTests'
                                }
                            }
                        }
                    }
                }
            }

            def testReport = tasks.register('testReport', TestReport) {
                destinationDirectory = reporting.baseDirectory.dir('allTests')
                testResults.from([
                    testing.suites.test.targets.test.testTask,
                    testing.suites.superTest.targets.superTest.testTask,
                    testing.suites.subTest.targets.subTest.testTask
                ])
            }

            tasks.named('build').configure { it.dependsOn testReport }
        """.stripIndent()

        and:
        file("src/test/java/org/gradle/testing/UnitTest.java") << """
            $packageAndImportsWithCategory
            public class UnitTest {
                @Test public void foo() {
                    System.out.println("org.gradle.testing.UnitTest#foo");
                }
            }
        """.stripIndent()
        file("src/test/java/org/gradle/testing/SuperTest.java") << """
            $packageAndImportsWithCategory
            public class SuperTest {
                @Category(SuperClassTests.class) @Test public void failing() {
                    System.out.println("org.gradle.testing.SuperTest#failing");
                    fail("failing test");
                }
                @Category(SuperClassTests.class) @Test public void passing() {
                    System.out.println("org.gradle.testing.SuperTest#passing");
                }
            }
        """.stripIndent()
        file("src/test/java/org/gradle/testing/SubTest.java") << """
            $packageAndImportsWithCategory
            public class SubTest {
                @Category(SubClassTests.class) @Test public void onlySub() {
                    System.out.println("org.gradle.testing.SubTest#onlySub " + System.getProperty("category"));
                    assertEquals("sub", System.getProperty("category"));
                }
                @Category(SubClassTests.class) @Test public void passing() {
                    System.out.println("org.gradle.testing.SubTest#passing " + System.getProperty("category"));
                }
            }
        """.stripIndent()
        file("src/test/java/org/gradle/testing/SuperClassTests.java") << """
            $packageAndImportsWithCategory
            public class SuperClassTests {
            }
        """.stripIndent()
        file("src/test/java/org/gradle/testing/SubClassTests.java") << """
            $packageAndImportsWithCategory
            public class SubClassTests extends SuperClassTests {
            }
        """.stripIndent()

        when:
        run "testReport"

        then:
        def htmlReport = new HtmlTestExecutionResult(testDirectory, 'build/reports/allTests')
        htmlReport.testClass("org.gradle.testing.UnitTest").assertTestCount(1, 0, 0).assertTestPassed("foo").assertStdout(equalTo('org.gradle.testing.UnitTest#foo\n'))
        htmlReport.testClass("org.gradle.testing.SuperTest").assertTestCount(2, 1, 0).assertTestPassed("passing")
            .assertTestFailed("failing", equalTo('java.lang.AssertionError: failing test'))
            .assertStdout(allOf(containsString('org.gradle.testing.SuperTest#failing\n'), containsString('org.gradle.testing.SuperTest#passing\n')))
        htmlReport.testClass("org.gradle.testing.SubTest").assertTestCount(4, 1, 0).assertTestPassed("passing") // onlySub is passing once and failing once
            .assertStdout(allOf(containsString('org.gradle.testing.SubTest#passing sub\n'),
                containsString('org.gradle.testing.SubTest#passing super\n'),
                containsString('org.gradle.testing.SubTest#onlySub sub\n'),
                containsString('org.gradle.testing.SubTest#onlySub super\n')))
    }

    def "report includes results of most recent invocation"() {
        given:
        buildFile << """
            $junitSetup
            test {
                def logLessStuff = providers.systemProperty('LogLessStuff')
                systemProperty 'LogLessStuff', logLessStuff.orNull
            }
        """.stripIndent()

        and:
        file("src/test/java/LoggingTest.java") << """
            import org.junit.Test;
            public class LoggingTest {
                @Test
                public void test() {
                    if (System.getProperty("LogLessStuff", "false").equals("true")) {
                        System.out.println("stdout.");
                        System.err.println("stderr.");
                    } else {
                        System.out.println("This is stdout.");
                        System.err.println("This is stderr.");
                    }
                }
            }
        """.stripIndent()

        when:
        run "test"

        then:
        def result = new HtmlTestExecutionResult(testDirectory)
        result.testClass("LoggingTest").assertStdout(equalTo("This is stdout.\n"))
        result.testClass("LoggingTest").assertStderr(equalTo("This is stderr.\n"))

        when:
        executer.withArguments("-DLogLessStuff=true")
        run "test"

        then:
        result.testClass("LoggingTest").assertStdout(equalTo("stdout.\n"))
        result.testClass("LoggingTest").assertStderr(equalTo("stderr.\n"))
    }

    // TODO: remove in Gradle 9.0
    def "nag with deprecation warnings when using legacy TestReport APIs"() {
        given:
        buildFile """
            apply plugin: 'java'
            $junitSetup
            tasks.register('otherTests', Test) {
                binaryResultsDirectory = file("bin")
                classpath = files('blahClasspath')
                testClassesDirs = files("blah")
            }
            tasks.register('testReport', TestReport) {
                reportOn test, otherTests
                destinationDir = reporting.file("tr")
            }
        """

        and:
        testClass("Thing")

        when:
        executer.expectDocumentedDeprecationWarning('The TestReport.reportOn(Object...) method has been deprecated. This is scheduled to be removed in Gradle 9.0. ' +
            'Please use the testResults method instead. ' +
            getTestReportLink("testResults"))
        executer.expectDocumentedDeprecationWarning('The TestReport.destinationDir property has been deprecated. ' +
            'This is scheduled to be removed in Gradle 9.0. Please use the destinationDirectory property instead. ' +
            getTestReportLink("destinationDir"))
        succeeds "testReport"

        then:
        skipped(":otherTests")
        executedAndNotSkipped(":test")
        new HtmlTestExecutionResult(testDirectory, "build/reports/tr").assertTestClassesExecuted("Thing")
    }

    @Issue("https://issues.gradle.org//browse/GRADLE-2915")
    def "test report task can handle tests tasks not having been executed"() {
        when:
        buildFile """
            apply plugin: 'java'

            $junitSetup

            tasks.register('testReport', TestReport) {
                testResults.from(tasks.named('test', Test))
                destinationDirectory = reporting.baseDirectory.dir("tr")
            }
        """

        and:
        testClass("Thing")

        then:
        succeeds "testReport"

        // verify incremental behavior on 2nd invocation
        succeeds "testReport"
        skipped ":testReport"
    }

    def "test report task is skipped when there are no results"() {
        given:
        buildFile << """
            ${junitSetup}
            tasks.register('testReport', TestReport) {
                testResults.from(tasks.named('test', Test))
                destinationDirectory = reporting.baseDirectory.dir('tr')
            }
        """

        when:
        succeeds "testReport"

        then:
        skipped(":test")
        skipped(":testReport")
    }

    def "#type report files are considered outputs"() {
        given:
        buildFile """
            $junitSetup
        """

        and:
        testClass "SomeTest"

        when:
        run "test"

        then:
        executedAndNotSkipped(":test")
        file(reportsDir).exists()

        when:
        run "test"

        then:
        skipped(":test")
        file(reportsDir).exists()

        when:
        file(reportsDir).deleteDir()
        run "test"

        then:
        executedAndNotSkipped(":test")
        file(reportsDir).exists()

        where:
        type   | reportsDir
        "xml"  | "build/test-results"
        "html" | "build/reports/tests"
    }

    def "merge rerun defaults to false"() {
        when:
        buildFile """
            $junitSetup
        """
        rerunningTest("SomeTest")
        fails "test"

        then:
        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("SomeTest")
        clazz.testCount == 6
        (clazz as JUnitTestClassExecutionResult).testCasesCount == 6
    }

    def "can enable merge rerun in xml report"() {
        when:
        buildFile """
            $junitSetup
            test.reports.junitXml.mergeReruns = true
        """
        rerunningTest("SomeTest")
        fails "test"

        then:
        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("SomeTest")
        clazz.testCount == 6
        (clazz as JUnitTestClassExecutionResult).testCasesCount == 2
        clazz.assertTestPassed("testFlaky[]")
        clazz.assertTestFailed("testFailing[]", CoreMatchers.anything())
    }

    // TODO: remove in Gradle 9.0
    def "using deprecated testReport elements emits deprecation warnings"() {
        when:
        buildFile """
            apply plugin: 'java'
            $junitSetup
            // Need a second test task to reportOn
            tasks.register('otherTests', Test) {
                binaryResultsDirectory = file('otherBin')
                classpath = files('otherClasspath')
                testClassesDirs = files('otherClasses')
            }
            tasks.register('testReport', TestReport) {
                reportOn test, otherTests
                destinationDir = reporting.file("myTestReports")
            }
        """

        then:
        executer.expectDocumentedDeprecationWarning('The TestReport.reportOn(Object...) method has been deprecated. ' +
            'This is scheduled to be removed in Gradle 9.0. ' +
            'Please use the testResults method instead. ' +
            getTestReportLink("testResults"))
        executer.expectDocumentedDeprecationWarning('The TestReport.destinationDir property has been deprecated. ' +
            'This is scheduled to be removed in Gradle 9.0. ' +
            'Please use the destinationDirectory property instead. ' +
            getTestReportLink("destinationDir"))
        succeeds "testReport"
    }

    protected static String getJunitSetup() {
        """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:${LATEST_JUNIT4_VERSION}'
            }
        """.stripIndent()
    }

    private static String getPackageAndImportsWithCategory() {
        return """
            package org.gradle.testing;

            import static org.junit.Assert.*;
            import org.junit.Test;
            import org.junit.experimental.categories.Category;
        """.stripIndent()
    }

    TestFile rerunningTest(String className) {
        file("src/test/java/${className}.java") << """
            import org.junit.Assert;
            import org.junit.Test;

            @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
            public class $className {

                @org.junit.runners.Parameterized.Parameters(name = "")
                public static Object[] data() {
                    return new Object[] { 1, 2, 3 };
                }

                private final int i;

                public SomeTest(int i) {
                    this.i = i;
                }

                @Test
                public void testFlaky() {
                    System.out.println("execution " + i);
                    Assert.assertTrue(i == 3);
                }

                @Test
                public void testFailing() {
                    System.out.println("execution " + i);
                    Assert.assertTrue(false);
                }
            }
        """
    }

    private getTestReportLink(sectionPart) {
        String.format(RECOMMENDATION, "information", "${BASE_URL}/dsl/org.gradle.api.tasks.testing.TestReport.html#org.gradle.api.tasks.testing.TestReport:${sectionPart}")
    }

    private void failingTestClass(String name) {
        testClass(name, true)
    }

    private void testClass(String name, boolean failing = false) {
        file("src/test/java/${name}.java") << """
            public class $name {
                @org.junit.Test
                public void test() {
                    assert false == ${failing};
                }
            }
        """
    }
}
