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
import org.gradle.integtests.fixtures.TestResultOutputAssociation
import spock.lang.Shared

import static org.gradle.integtests.fixtures.TestResultOutputAssociation.WITH_SUITE
import static org.gradle.integtests.fixtures.TestResultOutputAssociation.WITH_TESTCASE
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.anything
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not

class TestNGXmlResultAndHtmlReportIntegrationTest extends
        AbstractIntegrationSpec {

    static class Mode {
        String name
        TestResultOutputAssociation outputAssociation
        String config
    }

    @Shared
    Mode outputPerTestCase = new Mode(name: "output-per-testcase", outputAssociation: WITH_TESTCASE, config: "reports.junitXml.outputPerTestCase = true")
    @Shared
    Mode outputAtSuite = new Mode(name: "output-at-suite", outputAssociation: WITH_SUITE, config: "reports.junitXml.outputPerTestCase = false")

    @Shared List<Mode> modes = [outputAtSuite, outputPerTestCase]

    def setup() {
        executer.noExtraLogging()
        setupTestCases()
    }

    def "produces JUnit xml results - #mode.name"() {
        when:
        runWithTestConfig("useTestNG(); $mode.config")

        then:
        verify(mode)

        where:
        mode << modes
    }

    def "produces JUnit xml results when running tests in parallel - #mode.name"() {
        when:
        runWithTestConfig("useTestNG(); maxParallelForks = 2; $mode.config")

        then:
        verify(mode)

        where:
        mode << modes
    }

    def "produces JUnit xml results with aggressive forking - #mode.name"() {
        when:
        runWithTestConfig("useTestNG(); forkEvery = 1; $mode.config")

        then:
        verify(mode)

        where:
        mode << modes
    }

    void verify(Mode mode) {
        verifyTestResultWith(new JUnitXmlTestExecutionResult(file("."), mode.outputAssociation), mode.outputAssociation)
        verifyTestResultWith(new HtmlTestExecutionResult(file(".")), mode.outputAssociation)
    }

    def runWithTestConfig(String testConfiguration) {
        def buildFile = file('build.gradle')
        buildFile.text = """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:6.3.1' }

            test {
                $testConfiguration
            }
            """
        //when
        executer.withTasks('test').runWithFailure().assertTestsFailed()
    }

    def verifyTestResultWith(TestExecutionResult executionResult, TestResultOutputAssociation outputAssociation) {
        executionResult.assertTestClassesExecuted("org.FailingTest", "org.PassingTest", "org.MixedMethodsTest", "org.NoOutputsTest", "org.EncodingTest", "org.ParameterizedTest", "org.OutputLifecycleTest")

        def mixedMethods = executionResult.testClass("org.MixedMethodsTest")
                .assertTestCount(4, 2, 0)
                .assertTestsExecuted("passing", "passing2", "failing", "failing2")
                .assertTestFailed("failing", equalTo('java.lang.AssertionError: failing!'))
                .assertTestFailed("failing2", equalTo('java.lang.AssertionError: failing2!'))
                .assertTestPassed("passing")
                .assertTestPassed("passing2")
                .assertTestsSkipped()

        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            mixedMethods
                    .assertStderr(allOf(containsString("err.fail"), containsString("err.fail2"), containsString("err.pass"), containsString("err.pass2")))
                    .assertStderr(not(containsString("out.")))
                    .assertStdout(allOf(containsString("out.fail"), containsString("out.fail2"), containsString("out.pass"), containsString("out.pass2")))
                    .assertStdout(not(containsString("err.")))
        } else {
            mixedMethods
                    .assertTestCaseStdout("passing", equalTo("out.pass\n"))
                    .assertTestCaseStderr("passing", equalTo("err.pass\n"))
                    .assertTestCaseStdout("failing", equalTo("out.fail\n"))
                    .assertTestCaseStderr("failing", equalTo("err.fail\n"))
                    .assertTestCaseStdout("passing2", equalTo("out.pass2\n"))
                    .assertTestCaseStderr("passing2", equalTo("err.pass2\n"))
                    .assertTestCaseStdout("failing2", equalTo("out.fail2\n"))
                    .assertTestCaseStderr("failing2", equalTo("err.fail2\n"))
        }

        def passing = executionResult.testClass("org.PassingTest")
                .assertTestCount(2, 0, 0)
                .assertTestsExecuted("passing", "passing2")
                .assertTestPassed("passing").assertTestPassed("passing2")
        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            passing
                    .assertStdout(equalTo("out\n"))
                    .assertStderr(equalTo(""))
        } else {
            passing
                    .assertTestCaseStdout("passing", equalTo("out\n"))
                    .assertTestCaseStderr("passing", equalTo(""))
                    .assertTestCaseStdout("passing2", equalTo(""))
                    .assertTestCaseStderr("passing2", equalTo(""))
        }

        def failing = executionResult.testClass("org.FailingTest")
                .assertTestCount(2, 2, 0)
                .assertTestsExecuted("failing", "failing2")
                .assertTestFailed("failing", anything()).assertTestFailed("failing2", anything())

        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            failing
                    .assertStdout(equalTo(""))
                    .assertStderr(equalTo("err\n"))
        } else {
            failing
                    .assertTestCaseStdout("failing", equalTo(""))
                    .assertTestCaseStderr("failing", equalTo("err\n"))
                    .assertTestCaseStdout("failing2", equalTo(""))
                    .assertTestCaseStderr("failing2", equalTo(""))

        }

        def noOutputs = executionResult.testClass("org.NoOutputsTest")
                .assertTestCount(1, 0, 0)
                .assertTestsExecuted("passing").assertTestPassed("passing")

        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            noOutputs
                    .assertStdout(equalTo(""))
                    .assertStderr(equalTo(""))
        } else {
            noOutputs
                    .assertTestCaseStdout("passing", equalTo(""))
                    .assertTestCaseStderr("passing", equalTo(""))
        }

        def encoding = executionResult.testClass("org.EncodingTest")
                .assertTestCount(2, 1, 0)
                .assertTestPassed("encodesCdata")
                .assertTestFailed("encodesAttributeValues", equalTo('java.lang.RuntimeException: html: <> cdata: ]]> non-ascii: ż'))

        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            encoding
                    .assertStdout(equalTo("""< html allowed, cdata closing token ]]> encoded!
no EOL, non-ascii char: ż
xml entity: &amp;
"""))
                    .assertStderr(equalTo("< html allowed, cdata closing token ]]> encoded!\n"))
        } else {
            encoding
                    .assertTestCaseStdout("encodesCdata", equalTo("""< html allowed, cdata closing token ]]> encoded!
no EOL, non-ascii char: ż
xml entity: &amp;
"""))
                    .assertTestCaseStderr("encodesCdata", equalTo("< html allowed, cdata closing token ]]> encoded!\n"))
        }

        def parameterized = executionResult.testClass("org.ParameterizedTest")
                .assertTestCount(6, 4, 0)
                .assertTestsExecuted(
                "p1[0](1, 2)", "p4[0](1, \">…Ú)", "p1[1](3, 4)", "p3[0]", "p3[1]", "p4[1](2, \">…Ú)"
        )
                .assertTestFailed("p1[1](3, 4)", anything())
                .assertTestFailed("p3[0]", containsString("Parameter 2 of iteration 1 of method 'p3' toString() method threw exception"))
                .assertTestFailed("p3[1]", containsString("Parameter 2 of iteration 2 of method 'p3' toString() method threw exception"))
                .assertTestFailed("p4[1](2, \">…Ú)", anything())

        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            parameterized
                    .assertStdout(equalTo("var1 is: 1\nvar1 is: 3\n"))
                    .assertStderr(equalTo("var2 is: 2\nvar2 is: 4\n"))
        } else {
            parameterized
                    .assertTestCaseStdout("p1[0](1, 2)", equalTo("var1 is: 1\n"))
                    .assertTestCaseStdout("p1[1](3, 4)", equalTo("var1 is: 3\n"))
                    .assertTestCaseStderr("p1[0](1, 2)", equalTo("var2 is: 2\n"))
                    .assertTestCaseStderr("p1[1](3, 4)", equalTo("var2 is: 4\n"))
        }

        def outputLifecycle = executionResult.testClass("org.OutputLifecycleTest")
                .assertTestCount(2, 0, 0)
                .assertTestsExecuted("m1", "m2")
                .assertTestPassed("m1")
                .assertTestPassed("m1")
                .assertTestsSkipped()

        if (executionResult instanceof HtmlTestExecutionResult || outputAssociation == WITH_SUITE) {
            outputLifecycle
                    .assertStdout(allOf(containsString("m1 out"), containsString("m2 out")))
                    .assertStderr(allOf(containsString("m1 err"), containsString("m2 err")))

                    // We don't capture anything outside of test methods for TestNG
                    .assertStdout(not(anyOf(containsString("before"), containsString("after"), containsString("constructor"))))
                    .assertStderr(not(anyOf(containsString("before"), containsString("after"), containsString("constructor"))))
        } else {
            outputLifecycle
                    .assertTestCaseStdout("m1", equalTo("m1 out\n"))
                    .assertTestCaseStderr("m1", equalTo("m1 err\n"))
                    .assertTestCaseStdout("m2", equalTo("m2 out\n"))
                    .assertTestCaseStderr("m2", equalTo("m2 err\n"))
        }

        true
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

        file("src/test/java/org/ParameterizedTest.java") << """package org;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class ParameterizedTest {

    @Test(dataProvider = "1")
	public void p1(String var1, String var2) {
        System.out.println("var1 is: " + var1);
        System.err.println("var2 is: " + var2);
       	assertEquals(var1, "1");
	}

	@DataProvider(name = "1")
	public Object[][] provider1() {
		return new Object[][] {
		   {"1", "2"},
		   {"3", "4"}
	    };
	}

    @Test(dataProvider = "3")
	public void p3(int i, Object obj) {
	    assertTrue(i == 1);
	}

	@DataProvider(name = "3")
	public Object[][] provider3() {
		return new Object[][] {
		    {1, new Object() { public String toString() { throw new RuntimeException("bang!"); } } },
		    {2, new Object() { public String toString() { throw new RuntimeException("bang!"); } } }
	    };
	}

    @Test(dataProvider = "4")
	public void p4(int i, Object obj) {
	    assertTrue(i == 1);
	}

	@DataProvider(name = "4")
	public Object[][] provider4() {
		return new Object[][] {
		    {1, "\\">…Ú" },
		    {2, "\\">…Ú" }
	    };
	}

}
"""

    file("src/test/java/org/OutputLifecycleTest.java") << """package org;

import org.testng.annotations.*;
import static org.testng.Assert.*;

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

    @BeforeTest
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

    @AfterTest
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

    }
}
