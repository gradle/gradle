/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.util.GradleVersion

import static org.gradle.testing.fixture.TestNGCoverage.FIXED_ICLASS_LISTENER
import static org.gradle.testing.fixture.TestNGCoverage.STANDARD_COVERAGE
import static org.hamcrest.CoreMatchers.is

@TargetCoverage({STANDARD_COVERAGE})
class TestNGLoggingOutputCaptureIntegrationTest extends MultiVersionIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java"
            ${jcenterRepository()}
            dependencies { testCompile "org.testng:testng:$version" }
            test {
                useTestNG()
                reports.junitXml.outputPerTestCase = true
                onOutput { test, event -> print "\$test -> \$event.message" }
            }
        """

        file("src/test/java/FooTest.java") << """import org.testng.annotations.*;
            public class FooTest {
                static { System.out.println("static out"); System.err.println("static err"); }

                public FooTest() {
                    System.out.println("constructor out"); System.err.println("constructor err");
                }

                @BeforeClass public static void beforeClass() {
                    System.out.println("beforeClass out"); System.err.println("beforeClass err");
                }

                @BeforeTest public void beforeTest() {
                    System.out.println("beforeTest out"); System.err.println("beforeTest err");
                }

                @Test public void m1() {
                    System.out.print("m1: ");
                    System.out.print("\u03b1</html>");
                    System.out.println();
                    System.err.println("m1 err");
                }

                @Test public void m2() {
                    System.out.println("m2 out"); System.err.println("m2 err");
                }

                @AfterTest public void afterTest() {
                    System.out.println("afterTest out"); System.err.println("afterTest err");
                }

                @AfterClass public static void afterClass() {
                    System.out.println("afterClass out"); System.err.println("afterClass err");
                }
            }
        """
    }

    def "attaches events to correct test descriptors of a suite"() {
        buildFile << "test.useTestNG { suites 'suite.xml' }"

        file("suite.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="AwesomeSuite">
  <test name='The Foo Test'><classes><class name='FooTest'/></classes></test>
</suite>"""

        when: succeeds "test"

        then:
        containsLinesThatMatch(result.output,
            "Gradle Test Executor \\d+ -> static out",
            "Gradle Test Executor \\d+ -> static err",
            "Gradle Test Executor \\d+ -> constructor out",
            "Gradle Test Executor \\d+ -> constructor err"
        )

        outputContains expectedOutput('The Foo Test')

        /**
         * This test documents the current behavior. It's not right, we're missing a lot of output in the report.
         */

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = xmlReport.testClass("FooTest")

        assertTestClassExecutionResultOutput(classResult)

        def htmlReport = new HtmlTestExecutionResult(testDirectory)
        def classReport = htmlReport.testClass("FooTest")
        assertTestClassExecutionResultReport(classReport)
    }

    def "attaches output events to correct test descriptors"() {
        when: succeeds "test"

        then:
        containsLinesThatMatch(result.output,
            "Gradle Test Executor \\d+ -> static out",
            "Gradle Test Executor \\d+ -> static err",
            "Gradle Test Executor \\d+ -> constructor out",
            "Gradle Test Executor \\d+ -> constructor err"
        )

        outputContains expectedOutput('Gradle test')

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = xmlReport.testClass("FooTest")


        /**
         * This test documents the current behavior. It's not right, we're missing a lot of output in the report.
         */
        assertTestClassExecutionResultOutput(classResult)

        def htmlReport = new HtmlTestExecutionResult(testDirectory)
        def classReport = htmlReport.testClass("FooTest")
        assertTestClassExecutionResultReport(classReport)
    }

    boolean containsLinesThatMatch(String text, String... regexes) {
        return regexes.every { regex ->
            text.readLines().find { it.matches regex }
        }
    }

    private String expectedOutput(String testSuiteName) {
        providesClassListener() ? expectedEventOutputWithTestClassListener(testSuiteName) : expectedEventOutputWithoutTestClassListener(testSuiteName)
    }

    private void assertTestClassExecutionResultOutput(TestClassExecutionResult classResult) {
        classResult.assertTestCaseStderr("m1", is("m1 err\n"))
        classResult.assertTestCaseStderr("m2", is("m2 err\n"))
        classResult.assertTestCaseStdout("m1", is("m1: \u03b1</html>\n"))
        classResult.assertTestCaseStdout("m2", is("m2 out\n"))

        if (providesClassListener()) {
            classResult.assertStderr(is("beforeClass err\n"))
            classResult.assertStdout(is("beforeClass out\n"))
        } else {
            classResult.assertStderr(is(""))
            classResult.assertStdout(is(""))
        }
    }

    private void assertTestClassExecutionResultReport(TestClassExecutionResult classReport) {
        if (providesClassListener()) {
            classReport.assertStdout(is("beforeClass out\nm1: \u03b1</html>\nm2 out\n"))
            classReport.assertStderr(is("beforeClass err\nm1 err\nm2 err\n"))
        } else {
            classReport.assertStdout(is("m1: \u03b1</html>\nm2 out\n"))
            classReport.assertStderr(is("m1 err\nm2 err\n"))
        }
    }

    static boolean providesClassListener() {
        GradleVersion.version(version).compareTo(GradleVersion.version(FIXED_ICLASS_LISTENER)) == 1
    }

    static String expectedEventOutputWithoutTestClassListener(String testSuiteName) {
        """Test suite '$testSuiteName' -> beforeTest out
Test suite '$testSuiteName' -> beforeTest err
Test suite '$testSuiteName' -> beforeClass out
Test suite '$testSuiteName' -> beforeClass err
Test method m1(FooTest) -> m1: α</html>
Test method m1(FooTest) -> m1 err
Test method m2(FooTest) -> m2 out
Test method m2(FooTest) -> m2 err
Test suite '$testSuiteName' -> afterClass out
Test suite '$testSuiteName' -> afterClass err
Test suite '$testSuiteName' -> afterTest out
Test suite '$testSuiteName' -> afterTest err
"""
    }

    static String expectedEventOutputWithTestClassListener(String testSuiteName) {
        """Test suite '$testSuiteName' -> beforeTest out
Test suite '$testSuiteName' -> beforeTest err
Test class FooTest -> beforeClass out
Test class FooTest -> beforeClass err
Test method m1(FooTest) -> m1: α</html>
Test method m1(FooTest) -> m1 err
Test method m2(FooTest) -> m2 out
Test method m2(FooTest) -> m2 err
Test suite '$testSuiteName' -> afterClass out
Test suite '$testSuiteName' -> afterClass err
Test suite '$testSuiteName' -> afterTest out
Test suite '$testSuiteName' -> afterTest err
"""
    }
}
