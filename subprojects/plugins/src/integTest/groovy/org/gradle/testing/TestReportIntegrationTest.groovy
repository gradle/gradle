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
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Unroll

import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.equalTo

class TestReportIntegrationTest extends AbstractIntegrationSpec {
    @Rule Sample sample = new Sample(temporaryFolder)

    def "report includes results of each invocation"() {
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
            System.out.print("stdout.");
            System.err.print("stderr.");
        } else {
            System.out.print("This is stdout.");
            System.err.print("This is stderr.");
        }
    }
}
"""

        when:
        run "test"

        then:
        def result = new HtmlTestExecutionResult(testDirectory)
        result.testClass("LoggingTest").assertStdout(equalTo("This is stdout."))
        result.testClass("LoggingTest").assertStderr(equalTo("This is stderr."))

        when:
        executer.withArguments("-DLogLessStuff=true")
        run "test"

        then:
        result.testClass("LoggingTest").assertStdout(equalTo("stdout."))
        result.testClass("LoggingTest").assertStderr(equalTo("stderr."))
    }

    @UsesSample("testing/testReport")
    def "can generate report for subprojects"() {
        given:
        sample sample

        when:
        run "testReport"

        then:
        def htmlReport = new HtmlTestExecutionResult(sample.dir, "allTests")
        htmlReport.testClass("org.gradle.sample.CoreTest").assertTestCount(1, 0, 0).assertTestPassed("ok").assertStdout(contains("hello from CoreTest."))
        htmlReport.testClass("org.gradle.sample.UtilTest").assertTestCount(1, 0, 0).assertTestPassed("ok").assertStdout(contains("hello from UtilTest."))
    }


    @Unroll
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
        errorOutput.contains("See the report at: ")

        when:
        buildFile << "\ntest.reports.html.enabled = false\n"
        fails "test"

        then:
        ":test" in nonSkippedTasks
        errorOutput.contains("See the results at: ")

        when:
        buildFile << "\ntest.reports.junitXml.enabled = false\n"
        fails "test"

        then:
        ":test" in nonSkippedTasks
        errorOutput.contains("There were failing tests")
        !errorOutput.contains("See the")
    }


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


    String getJunitSetup() {
        """
        apply plugin: 'java'
        repositories { mavenCentral() }
        dependencies { testCompile 'junit:junit:4.11' }
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
