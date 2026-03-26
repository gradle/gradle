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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.TestPathExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitTestClassExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo

class TestReportTaskIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Rule
    Sample sample = new Sample(temporaryFolder)

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT4
    }

    @UsesSample("testing/testReport/groovy")
    def "can generate report for subprojects"() {
        given:
        super.sample(sample)

        when:
        run "testReport"

        then:
        GenericTestExecutionResult testResults = resultsFor(testDirectory.file("testReport"), "allTests")

        TestPathExecutionResult coreTest = testResults.testPath("org.gradle.sample.CoreTest:ok")
        coreTest.onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        coreTest.onlyRoot().assertStdout(equalTo("hello from CoreTest.\n"))

        TestPathExecutionResult utilTest = testResults.testPath("org.gradle.sample.UtilTest:ok")
        utilTest.onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        utilTest.onlyRoot().assertStdout(equalTo("hello from UtilTest.\n"))
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
                                options {
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
                                options {
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
        def htmlReport = resultsFor(testDirectory, 'allTests')

        htmlReport.testPath("org.gradle.testing.UnitTest").onlyRoot().assertChildCount(1, 0)
        htmlReport.testPath("org.gradle.testing.UnitTest", "foo").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(equalTo('org.gradle.testing.UnitTest#foo\n'))

        htmlReport.testPath("org.gradle.testing.SuperTest").onlyRoot().assertChildCount(2, 1)
        htmlReport.testPath("org.gradle.testing.SuperTest", "failing").onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsString('java.lang.AssertionError: failing test'))
            .assertStdout(containsString('org.gradle.testing.SuperTest#failing\n'))

        htmlReport.testPath("org.gradle.testing.SuperTest", "passing").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(containsString('org.gradle.testing.SuperTest#passing\n'))

        htmlReport.testPath("org.gradle.testing.SubTest").root("Gradle Test Run :superTest").assertChildCount(2, 1)
        htmlReport.testPath("org.gradle.testing.SubTest", "onlySub").root("Gradle Test Run :superTest")
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertStdout(containsString('org.gradle.testing.SubTest#onlySub super\n'))

        htmlReport.testPath("org.gradle.testing.SubTest", "passing").root("Gradle Test Run :superTest")
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(containsString('org.gradle.testing.SubTest#passing super\n'))

        htmlReport.testPath("org.gradle.testing.SuperTest", "failing").root("Gradle Test Run :superTest")
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertStdout(containsString('org.gradle.testing.SuperTest#failing\n'))
            .assertFailureMessages(containsString('java.lang.AssertionError: failing test'))

        htmlReport.testPath("org.gradle.testing.SuperTest", "passing").root("Gradle Test Run :superTest")
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(containsString('org.gradle.testing.SuperTest#passing\n'))

        htmlReport.testPath("org.gradle.testing.SubTest").root("Gradle Test Run :subTest").assertChildCount(2, 0)
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
        resultsFor("tests/test").testPath("LoggingTest", "test").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(equalTo("This is stdout.\n"))
            .assertStderr(equalTo("This is stderr.\n"))

        when:
        executer.withArguments("-DLogLessStuff=true")
        run "test"

        then:
        resultsFor("tests/test", GenericTestExecutionResult.TestFramework.JUNIT4).testPath("LoggingTest", "test").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(equalTo("stdout.\n"))
            .assertStderr(equalTo("stderr.\n"))
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
