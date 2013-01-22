/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult

import static org.hamcrest.Matchers.*

public class TestNGXmlResultAndHtmlReportIntegrationTest extends
        AbstractIntegrationSpec {

    def setup() {
        executer.noExtraLogging()
        setupTestCases()
    }

    def "produces JUnit xml results"() {
        when:
        runWithTestConfig("useTestNG()")
        then:
        verifyTestResultWith(new JUnitXmlTestExecutionResult(file(".")))
        verifyTestResultWith(new HtmlTestExecutionResult(file(".")))
    }

    def "produces JUnit xml results when running tests in parallel"() {
        when:
        runWithTestConfig("useTestNG(); maxParallelForks 2")
        then:
        verifyTestResultWith(new JUnitXmlTestExecutionResult(file(".")))
        verifyTestResultWith(new HtmlTestExecutionResult(file(".")))
    }

    def "produces JUnit xml results with aggressive forking"() {
        when:
        runWithTestConfig("useTestNG(); forkEvery 1")
        then:
        verifyTestResultWith(new JUnitXmlTestExecutionResult(file(".")))
        verifyTestResultWith(new HtmlTestExecutionResult(file(".")))
    }

    def runWithTestConfig(String testConfiguration) {
        def buildFile = file('build.gradle')
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'org.testng:testng:6.3.1' }

            test {
                $testConfiguration
            }
            """
        //when
        executer.withTasks('test').runWithFailure().assertTestsFailed()
    }

    def verifyTestResultWith(TestExecutionResult executionResult) {
        executionResult.assertTestClassesExecuted("org.FailingTest", "org.PassingTest", "org.MixedMethodsTest", "org.NoOutputsTest", "org.EncodingTest")

        executionResult.testClass("org.MixedMethodsTest")
                .assertTestCount(4, 2, 0)
                .assertTestsExecuted("passing", "passing2", "failing", "failing2")
                .assertTestFailed("failing", equalTo('java.lang.AssertionError: failing!'))
                .assertTestFailed("failing2", equalTo('java.lang.AssertionError: failing2!'))
                .assertTestPassed("passing")
                .assertTestPassed("passing2")
                .assertTestsSkipped()
                .assertStderr(allOf(containsString("err.fail"), containsString("err.fail2"), containsString("err.pass"), containsString("err.pass2")))
                .assertStderr(not(containsString("out.")))
                .assertStdout(allOf(containsString("out.fail"), containsString("out.fail2"), containsString("out.pass"), containsString("out.pass2")))
                .assertStdout(not(containsString("err.")))

        executionResult.testClass("org.PassingTest")
                .assertTestCount(2, 0, 0)
                .assertTestsExecuted("passing", "passing2")
                .assertTestPassed("passing").assertTestPassed("passing2")
                .assertStdout(equalTo("out\n"))
                .assertStderr(equalTo(""))

        executionResult.testClass("org.FailingTest")
                .assertTestCount(2, 2, 0)
                .assertTestsExecuted("failing", "failing2")
                .assertTestFailed("failing", anything()).assertTestFailed("failing2", anything())
                .assertStdout(equalTo(""))
                .assertStderr(equalTo("err\n"))

        executionResult.testClass("org.NoOutputsTest")
                .assertTestCount(1, 0, 0)
                .assertTestsExecuted("passing").assertTestPassed("passing")
                .assertStdout(equalTo(""))
                .assertStderr(equalTo(""))

        executionResult.testClass("org.EncodingTest")
                .assertTestCount(2, 1, 0)
                .assertTestPassed("encodesCdata")
                .assertTestFailed("encodesAttributeValues", equalTo('java.lang.RuntimeException: html: <> cdata: ]]> non-ascii: ż'))
                .assertStdout(equalTo("""< html allowed, cdata closing token ]]> encoded!
no EOL, non-ascii char: ż
xml entity: &amp;
"""))
                .assertStderr(equalTo("< html allowed, cdata closing token ]]> encoded!\n"))
    }


    private void setupTestCases() {
        file("src/test/java/org/MixedMethodsTest.java") << """package org;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class MixedMethodsTest {
    @Test public void passing() {
        System.out.println("out.pass");
        System.err.println("err.pass");
    }
    @Test public void failing() {
        System.out.println("out.fail");
        System.err.println("err.fail");
        fail("failing!");
    }
    @Test public void passing2() {
        System.out.println("out.pass2");
        System.err.println("err.pass2");
    }
    @Test public void failing2() {
        System.out.println("out.fail2");
        System.err.println("err.fail2");
        fail("failing2!");
    }
    @Test(enabled = false) public void skipped() {}
}
"""
        file("src/test/java/org/PassingTest.java") << """package org;
import org.testng.annotations.*;

public class PassingTest {
    @Test public void passing() {
        System.out.println("out" );
    }
    @Test public void passing2() {}
}
"""
        file("src/test/java/org/FailingTest.java") << """package org;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class FailingTest {
    @Test public void failing() {
        System.err.println("err");
        fail();
    }
    @Test public void failing2() {
        fail();
    }
}
"""
        file("src/test/java/org/NoOutputsTest.java") << """package org;
import org.testng.annotations.*;

public class NoOutputsTest {
    @Test(enabled=false) public void skipped() {}
    @Test public void passing() {}
}
"""

        file("src/test/java/org/EncodingTest.java") << """package org;
import org.testng.annotations.*;

public class EncodingTest {
    @Test public void encodesCdata() {
        System.out.println("< html allowed, cdata closing token ]]> encoded!");
        System.out.print("no EOL, ");
        System.out.println("non-ascii char: ż");
        System.out.println("xml entity: &amp;");
        System.err.println("< html allowed, cdata closing token ]]> encoded!");
    }
    @Test public void encodesAttributeValues() {
        throw new RuntimeException("html: <> cdata: ]]> non-ascii: ż");
    }
}
"""
    }
}
