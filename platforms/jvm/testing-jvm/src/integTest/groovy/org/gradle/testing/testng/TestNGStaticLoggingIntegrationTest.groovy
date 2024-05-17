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
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is

class TestNGStaticLoggingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        TestNGCoverage.enableTestNG(buildFile)
        buildFile << """
            test {
                reports.junitXml.outputPerTestCase = true
                onOutput { test, event -> print "\$test -> \$event.message" }
            }
        """
    }

    @Issue("GRADLE-2841")
    def "captures output from logging frameworks"() {
        buildFile << """
            dependencies { implementation "org.slf4j:slf4j-simple:1.7.10", "org.slf4j:slf4j-api:1.7.10" }
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

        when: succeeds("test")

        then:
        outputContains("Test method foo(FooTest) -> [Test worker] INFO FooTest - slf4j info")
        outputContains("Test method foo(FooTest) -> ${java.util.logging.Level.INFO.getLocalizedName()}: jul info")
        outputContains("Test method foo(FooTest) -> ${java.util.logging.Level.WARNING.getLocalizedName()}: jul warning")

        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = testResult.testClass("FooTest")
        classResult.assertTestCaseStderr("foo", containsString("[Test worker] INFO FooTest - slf4j info"))
        classResult.assertTestCaseStderr("foo", containsString("${java.util.logging.Level.INFO.getLocalizedName()}: jul info"))
        classResult.assertTestCaseStderr("foo", containsString("${java.util.logging.Level.WARNING.getLocalizedName()}: jul warning"))
    }

    @Issue("GRADLE-2841")
    def "captures logging from System streams referenced from static initializer"() {
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

        when: succeeds("test")
        then:
        outputContains("Test method foo(FooTest) -> cool output from test")
        outputContains("Test method foo(FooTest) -> err output from test")
        result.output.readLines().find { it.matches "Gradle Test Executor \\d+ -> cool output from initializer" }

        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        testResult.testClass("FooTest").assertTestCaseStdout("foo", is("cool output from test\n"))
        testResult.testClass("FooTest").assertTestCaseStderr("foo", is("err output from test\n"))
    }

    def "test can generate output from multiple threads"() {
        file("src/test/java/OkTest.java") << """
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import org.testng.annotations.*;

public class OkTest {
    @Test
    public void ok() throws Exception {
        // logging from multiple threads
        List<Thread> threads  = new ArrayList<Thread>();
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread("thread " + i) {
                @Override
                public void run() {
                    System.out.print("stdout from "); // print a partial line
                    System.err.println("stderr from " + getName());
                    System.out.println(getName());
                    Logger.getLogger("test-logger").info("info from " + getName());
                }
            };
            thread.start();
            threads.add(thread);
        }
        for(Thread thread: threads) {
            thread.join();
        }
    }
}
"""

        when:
        succeeds("test")

        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = testResult.testClass("OkTest")

        5.times { n ->
            outputContains("Test method ok(OkTest) -> stdout from thread $n")
            outputContains("Test method ok(OkTest) -> stderr from thread $n")
            outputContains("Test method ok(OkTest) -> ${java.util.logging.Level.INFO.getLocalizedName()}: info from thread $n")

            classResult.assertTestCaseStdout("ok", containsString("stdout from thread $n"))
            classResult.assertTestCaseStderr("ok", containsString("stderr from thread $n"))
            classResult.assertTestCaseStderr("ok", containsString("${java.util.logging.Level.INFO.getLocalizedName()}: info from thread $n"))
        }
    }

}
