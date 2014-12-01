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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.is

class TestNGStaticLoggingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2841")
    def "captures output from logging frameworks"() {
        TestNGCoverage.enableTestNG(buildFile)
        buildFile << """
            test {
                reports.junitXml.outputPerTestCase = true
                onOutput { test, event -> print "\$test -> \$event.message" }
            }
            dependencies { compile "org.slf4j:slf4j-simple:1.7.7", "org.slf4j:slf4j-api:1.7.7" }
        """

        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                private final static org.slf4j.Logger SLF4J = org.slf4j.LoggerFactory.getLogger(FooTest.class);
                private final static java.util.logging.Logger JUL = java.util.logging.Logger.getLogger(FooTest.class.getName());

                @Test public void foo() {
                  SLF4J.info("slf4j info");
                  JUL.info("jul info");
                  JUL.warning("jul warning");
                }
            }
        """

        when: run("test")

        then:
        result.output.contains("test method foo(FooTest) -> [Test worker] INFO FooTest - slf4j info")
        result.output.contains("test method foo(FooTest) -> INFO: jul info")
        result.output.contains("test method foo(FooTest) -> WARNING: jul warning")

        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = testResult.testClass("FooTest")
        classResult.assertTestCaseStderr("foo", containsString("[Test worker] INFO FooTest - slf4j info"))
        classResult.assertTestCaseStderr("foo", containsString("INFO: jul info"))
        classResult.assertTestCaseStderr("foo", containsString("WARNING: jul warning"))
    }

    @Issue("GRADLE-2841")
    def "captures logging from System streams referenced from static initializer"() {
        TestNGCoverage.enableTestNG(buildFile)
        buildFile << "test.onOutput { id, event -> println 'captured ' + event.message }"

        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;
            import java.io.PrintStream;

            public class FooTest {
                static PrintStream out = System.out;
                static PrintStream err = System.err;
                static { out.println("cool output from initializer"); }
                @Test public void foo() { out.println("cool output from test"); err.println("err output from test"); }
            }
        """

        when: run("test")
        then:
        result.output.contains("captured cool output from test")
        result.output.contains("captured err output from test")
        result.output.contains("captured cool output from initializer")

        def testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.testClass("FooTest").assertStdout(is("cool output from test\n"))
        testResult.testClass("FooTest").assertStderr(is("err output from test\n"))
    }
}
