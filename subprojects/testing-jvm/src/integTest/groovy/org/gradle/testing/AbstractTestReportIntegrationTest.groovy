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

import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

import static org.hamcrest.CoreMatchers.equalTo

abstract class AbstractTestReportIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {

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
            ${testFrameworkImports}
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

    def "test report task can handle test tasks that did not run tests"() {
        given:
        buildScript """
            $junitSetup

            def test = tasks.named('test', Test)

            def otherTests = tasks.register('otherTests', Test) {
                binaryResultsDirectory = file("bin")
                classpath = files('blahClasspath')
                testClassesDirs = files("blah")
                ${configureTestFramework}
            }

            tasks.register('testReport', TestReport) {
                testResults.from(test, otherTests)
                destinationDirectory = reporting.baseDirectory.dir('tr')
            }
        """

        and:
        testClass("Thing")

        when:
        succeeds "testReport"

        then:
        skipped(":otherTests")
        executedAndNotSkipped(":test")
        new HtmlTestExecutionResult(testDirectory, "build/reports/tr").assertTestClassesExecuted("Thing")
    }

    def "results or reports are linked to in error output"() {
        given:
        buildScript """
            $junitSetup
            test {
                reports.all { it.required = true }
            }
        """

        and:
        failingTestClass "SomeTest"

        when:
        fails "test"

        then:
        executedAndNotSkipped(":test")
        failure.assertHasCause("There were failing tests. See the report at: ")

        when:
        buildFile << "\ntest.reports.html.required = false\n"
        fails "test"

        then:
        executedAndNotSkipped(":test")
        failure.assertHasCause("There were failing tests. See the results at: ")

        when:
        buildFile << "\ntest.reports.junitXml.required = false\n"
        fails "test"

        then:
        executedAndNotSkipped(":test")
        failure.assertHasCause("There were failing tests")
        failure.assertHasNoCause("See the")
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
        executedAndNotSkipped(":test")

        when:
        buildFile << "\ntest.reports.junitXml.outputPerTestCase = true\n"
        succeeds "test"

        then:
        executedAndNotSkipped(":test")
    }

    protected String getJunitSetup() {
        """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()
    }

    protected void failingTestClass(String name) {
        testClass(name, true)
    }

    protected void testClass(String name, boolean failing = false) {
        file("src/test/java/${name}.java") << """
            ${testFrameworkImports}
            public class $name {
                @Test
                public void test() {
                    assert false == ${failing};
                }
            }
        """
    }
}
