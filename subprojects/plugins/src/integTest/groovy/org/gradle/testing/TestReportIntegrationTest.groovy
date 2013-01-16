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
import org.junit.Rule
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import static org.hamcrest.Matchers.equalTo

class TestReportIntegrationTest extends AbstractIntegrationSpec {
    @Rule Sample sample

    def "report includes results of each invocation"() {
        given:
        buildFile << """
apply plugin: 'java'
repositories { mavenCentral() }
dependencies { testCompile 'junit:junit:4.11' }
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
        def result = new DefaultTestExecutionResult(testDirectory)
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
        def reportDir = sample.dir.file("build/reports/allTests")
        reportDir.file("index.html").assertIsFile()
        reportDir.file("org.gradle.sample.CoreTest.html").text.contains("hello from CoreTest.")
        reportDir.file("org.gradle.sample.UtilTest.html").text.contains("hello from UtilTest.")
    }
}
