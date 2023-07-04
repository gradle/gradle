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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is

abstract class AbstractJUnitLoggingOutputCaptureIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def setup() {
        buildFile << """
            apply plugin: "java"
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                reports.junitXml.outputPerTestCase = true
                // JUnit 5's test name contains parentheses
                onOutput { test, event -> print "\${test.toString().replace('()(', '(')} -> \$event.message" }
            }
        """.stripIndent()
    }

    def "captures output from logging frameworks"() {
        buildFile << """
            dependencies { testImplementation "org.slf4j:slf4j-simple:1.7.10", "org.slf4j:slf4j-api:1.7.10" }
        """.stripIndent()
        file("src/test/java/FooTest.java") << """
            ${testFrameworkImports}
            public class FooTest {
                private final static org.slf4j.Logger SLF4J = org.slf4j.LoggerFactory.getLogger(FooTest.class);
                private final static java.util.logging.Logger JUL = java.util.logging.Logger.getLogger(FooTest.class.getName());

                @Test
                public void foo() {
                  SLF4J.info("slf4j info");
                  JUL.info("jul info");
                  JUL.warning("jul warning");
                }
            }
        """.stripIndent()

        when:
        succeeds("test")

        then:
        outputContains("Test foo(FooTest) -> [Test worker] INFO FooTest - slf4j info")
        outputContains("Test foo(FooTest) -> ${java.util.logging.Level.INFO.getLocalizedName()}: jul info")
        outputContains("Test foo(FooTest) -> ${java.util.logging.Level.WARNING.getLocalizedName()}: jul warning")

        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = testResult.testClass("FooTest")
        classResult.assertTestCaseStderr("foo", containsString("[Test worker] INFO FooTest - slf4j info"))
        classResult.assertTestCaseStderr("foo", containsString("${java.util.logging.Level.INFO.getLocalizedName()}: jul info"))
        classResult.assertTestCaseStderr("foo", containsString("${java.util.logging.Level.WARNING.getLocalizedName()}: jul warning"))
    }

    def "test can generate output from multiple threads"() {
        file("src/test/java/OkTest.java") << """
            import java.util.logging.Logger;
            import java.util.List;
            import java.util.ArrayList;

            ${testFrameworkImports}

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
        """.stripIndent()

        when:
        succeeds("test")

        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = testResult.testClass("OkTest")

        5.times { n ->
            outputContains("Test ok(OkTest) -> stdout from thread $n")
            outputContains("Test ok(OkTest) -> stderr from thread $n")
            outputContains("Test ok(OkTest) -> ${java.util.logging.Level.INFO.getLocalizedName()}: info from thread $n")

            classResult.assertTestCaseStdout("ok", containsString("stdout from thread $n"))
            classResult.assertTestCaseStderr("ok", containsString("stderr from thread $n"))
            classResult.assertTestCaseStderr("ok", containsString("${java.util.logging.Level.INFO.getLocalizedName()}: info from thread $n"))
        }
    }

    def "output does not require trailing end-of-line separator"() {
        file("src/test/java/OkTest.java") << """
            ${testFrameworkImports}

            public class OkTest {
                ${beforeTestAnnotation}
                public void before() {
                    System.out.print("[before out]");
                    System.err.print("[before err]");
                }

                ${afterTestAnnotation}
                public void after() {
                    System.out.print("[after out]");
                    System.err.print("[after err]");
                }

                ${beforeClassAnnotation}
                public static void init() {
                    System.out.print("[before class out]");
                    System.err.print("[before class err]");
                }

                ${afterClassAnnotation}
                public static void end() {
                    System.out.print("[after class out]");
                    System.err.print("[after class err]");
                }

                @Test
                public void ok() {
                    System.out.print("[test out]");
                    System.err.print("[test err]");
                }

                @Test
                public void anotherOk() {
                    System.out.println();
                    System.err.println();
                    System.out.print("[ok out]");
                    System.err.print("[ok err]");
                }
            }
        """.stripIndent()

        when:
        run "test"

        then:
        def testResult = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = testResult.testClass("OkTest")
        classResult.assertTestCaseStdout("ok", is("[before out][test out][after out]"))
        classResult.assertTestCaseStderr("ok", is("[before err][test err][after err]"))
        classResult.assertTestCaseStdout("anotherOk", is("[before out]\n[ok out][after out]"))
        classResult.assertTestCaseStderr("anotherOk", is("[before err]\n[ok err][after err]"))
        classResult.assertStdout(is("[before class out][after class out]"))
        classResult.assertStderr(is("[before class err][after class err]"))
    }
}
