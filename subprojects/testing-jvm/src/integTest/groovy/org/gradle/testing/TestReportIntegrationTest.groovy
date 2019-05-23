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

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testing.fixture.JUnitCoverage.*
import static org.hamcrest.CoreMatchers.*

// https://github.com/junit-team/junit5/issues/1285
@TargetCoverage({ JUNIT_4_LATEST + emptyIfJava7(JUPITER, VINTAGE) })
class TestReportIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    @Rule Sample sample = new Sample(temporaryFolder)

    def "report includes results of most recent invocation"() {
        given:
        buildFile << """
$junitSetup
test { systemProperty 'LogLessStuff', System.getProperty('LogLessStuff') }
"""

        and:
        file("src/test/java/LoggingTest.java") << """
public class LoggingTest {
    @org.junit.Test
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
"""

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

    @UsesSample("testing/testReport/groovy")
    def "can generate report for subprojects"() {
        given:
        sample sample

        when:
        run "testReport"

        then:
        def htmlReport = new HtmlTestExecutionResult(sample.dir, "build/reports/allTests")
        htmlReport.testClass("org.gradle.sample.CoreTest").assertTestCount(1, 0, 0).assertTestPassed("ok").assertStdout(equalTo("hello from CoreTest.\n"))
        htmlReport.testClass("org.gradle.sample.UtilTest").assertTestCount(1, 0, 0).assertTestPassed("ok").assertStdout(equalTo("hello from UtilTest.\n"))
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "merges report with duplicated classes and methods"() {
        given:
        ignoreWhenJupiter()
        buildFile << """
$junitSetup
test {
    ignoreFailures true
    useJUnit {
        excludeCategories 'org.gradle.testing.SuperClassTests'
        excludeCategories 'org.gradle.testing.SubClassTests'
    }
}

task superTest(type: Test) {
    ignoreFailures true
    systemProperty 'category', 'super'
    useJUnit {
        includeCategories 'org.gradle.testing.SuperClassTests'
    }
}

task subTest(type: Test) {
    ignoreFailures true
    systemProperty 'category', 'sub'
    useJUnit {
        includeCategories 'org.gradle.testing.SubClassTests'
    }
}

task testReport(type: TestReport) {
    destinationDir = file("\$buildDir/reports/allTests")
    reportOn test, superTest, subTest
    tasks.build.dependsOn testReport
}
"""

        and:
        file("src/test/java/org/gradle/testing/UnitTest.java") << """
$testFilePrelude
public class UnitTest {
    @Test public void foo() {
        System.out.println("org.gradle.testing.UnitTest#foo");
    }
}
"""
        file("src/test/java/org/gradle/testing/SuperTest.java") << """
$testFilePrelude
public class SuperTest {
    @Category(SuperClassTests.class) @Test public void failing() {
        System.out.println("org.gradle.testing.SuperTest#failing");
        fail("failing test");
    }
    @Category(SuperClassTests.class) @Test public void passing() {
        System.out.println("org.gradle.testing.SuperTest#passing");
    }
}
"""
        file("src/test/java/org/gradle/testing/SubTest.java") << """
$testFilePrelude
public class SubTest {
    @Category(SubClassTests.class) @Test public void onlySub() {
        System.out.println("org.gradle.testing.SubTest#onlySub " + System.getProperty("category"));
        assertEquals("sub", System.getProperty("category"));
    }
    @Category(SubClassTests.class) @Test public void passing() {
        System.out.println("org.gradle.testing.SubTest#passing " + System.getProperty("category"));
    }
}
"""
        file("src/test/java/org/gradle/testing/SuperClassTests.java") << """
$testFilePrelude
public class SuperClassTests {
}
"""
        file("src/test/java/org/gradle/testing/SubClassTests.java") << """
$testFilePrelude
public class SubClassTests extends SuperClassTests {
}
"""

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

    @Issue("https://issues.gradle.org//browse/GRADLE-2821")
    @IgnoreIf({GradleContextualExecuter.parallel})
    def "test report task can handle test tasks that did not run tests"() {
        given:
        buildScript """
            apply plugin: 'java'

             $junitSetup

            task otherTests(type: Test) {
                binResultsDir file("bin")
                testClassesDirs = files("blah")
            }

            task testReport(type: TestReport) {
                reportOn test, otherTests
                destinationDir reporting.file("tr")
            }
        """

        and:
        testClass("Thing")

        when:
        succeeds "testReport"

        then:
        ":otherTests" in skippedTasks
        ":test" in nonSkippedTasks
        new HtmlTestExecutionResult(testDirectory, "build/reports/tr").assertTestClassesExecuted("Thing")
    }

    @Issue("https://issues.gradle.org//browse/GRADLE-2915")
    def "test report task can handle tests tasks not having been executed"() {
        when:
        buildScript """
            apply plugin: 'java'

             $junitSetup

            task testReport(type: TestReport) {
                testResultDirs = [test.binResultsDir]
                destinationDir reporting.file("tr")
            }
        """

        and:
        testClass("Thing")

        then:
        succeeds "testReport"
        succeeds "testReport"
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "test report task is skipped when there are no results"() {
        given:
        buildScript """
            apply plugin: 'java'

            task testReport(type: TestReport) {
                reportOn test
                destinationDir reporting.file("tr")
            }
        """

        when:
        succeeds "testReport"

        then:
        ":test" in skippedTasks
        ":testReport" in skippedTasks
    }

    @Unroll
    @IgnoreIf({GradleContextualExecuter.parallel})
    "#type report files are considered outputs"() {
        given:
        buildScript """
            $junitSetup
        """

        and:
        testClass "SomeTest"

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
        file(reportsDir).exists()

        when:
        run "test"

        then:
        ":test" in skippedTasks
        file(reportsDir).exists()

        when:
        file(reportsDir).deleteDir()
        run "test"

        then:
        ":test" in nonSkippedTasks
        file(reportsDir).exists()

        where:
        type   | reportsDir
        "xml"  | "build/test-results"
        "html" | "build/reports/tests"
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "results or reports are linked to in error output"() {
        given:
        buildScript """
            $junitSetup
            test {
                reports.all { it.enabled = true }
            }
        """

        and:
        failingTestClass "SomeTest"

        when:
        fails "test"

        then:
        ":test" in nonSkippedTasks
        failure.assertHasCause("There were failing tests. See the report at: ")

        when:
        buildFile << "\ntest.reports.html.enabled = false\n"
        fails "test"

        then:
        ":test" in nonSkippedTasks
        failure.assertHasCause("There were failing tests. See the results at: ")

        when:
        buildFile << "\ntest.reports.junitXml.enabled = false\n"
        fails "test"

        then:
        ":test" in nonSkippedTasks
        failure.assertHasCause("There were failing tests")
        failure.assertHasNoCause("See the")
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "output per test case flag invalidates outputs"() {
        when:
        buildScript """
            $junitSetup
            test.reports.junitXml.outputPerTestCase = false
        """
        testClass "SomeTest"
        succeeds "test"

        then:
        ":test" in nonSkippedTasks

        when:
        buildFile << "\ntest.reports.junitXml.outputPerTestCase = true\n"
        succeeds "test"

        then:
        ":test" in nonSkippedTasks
    }

    def "outputs over lifecycle"() {
        when:
        buildScript """
            $junitSetup
            test.reports.junitXml.outputPerTestCase = true
        """

        file("src/test/java/OutputLifecycleTest.java") << """
            import org.junit.*;

            public class OutputLifecycleTest {

                public OutputLifecycleTest() {
                    System.out.println("constructor out");
                    System.err.println("constructor err");
                }

                @BeforeClass
                public static void beforeClass() {
                    System.out.println("beforeClass out");
                    System.err.println("beforeClass err");
                }

                @Before
                public void beforeTest() {
                    System.out.println("beforeTest out");
                    System.err.println("beforeTest err");
                }

                @Test public void m1() {
                    System.out.println("m1 out");
                    System.err.println("m1 err");
                }

                @Test public void m2() {
                    System.out.println("m2 out");
                    System.err.println("m2 err");
                }

                @After
                public void afterTest() {
                    System.out.println("afterTest out");
                    System.err.println("afterTest err");
                }

                @AfterClass
                public static void afterClass() {
                    System.out.println("afterClass out");
                    System.err.println("afterClass err");
                }
            }
        """

        succeeds "test"

        then:
        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("OutputLifecycleTest")
        clazz.assertTestCaseStderr("m1", is("beforeTest err\nm1 err\nafterTest err\n"))
        clazz.assertTestCaseStderr("m2", is("beforeTest err\nm2 err\nafterTest err\n"))
        clazz.assertTestCaseStdout("m1", is("beforeTest out\nm1 out\nafterTest out\n"))
        clazz.assertTestCaseStdout("m2", is("beforeTest out\nm2 out\nafterTest out\n"))
        clazz.assertStderr(is("beforeClass err\nconstructor err\nconstructor err\nafterClass err\n"))
        clazz.assertStdout(is("beforeClass out\nconstructor out\nconstructor out\nafterClass out\n"))
    }

    String getJunitSetup() {
        """
        apply plugin: 'java'
        ${mavenCentralRepository()}
        dependencies { testCompile 'junit:junit:4.12' }
        """
    }

    String getTestFilePrelude() {
        """
package org.gradle.testing;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.experimental.categories.Category;
"""
    }
    void failingTestClass(String name) {
        testClass(name, true)
    }

    void testClass(String name, boolean failing = false) {
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
