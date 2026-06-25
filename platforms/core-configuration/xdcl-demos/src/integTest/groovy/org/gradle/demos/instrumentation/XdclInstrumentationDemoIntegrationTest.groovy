/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.instrumentation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(TestExecutionPreconditions.IsEmbeddedExecutor)
class XdclInstrumentationDemoIntegrationTest extends AbstractIntegrationSpec {

    private void applyEcosystems(String rootName = null) {
        file('settings.gradle.xdcl') << """
            settings {
              plugins [
                { id "java-ecosystem" },
                { id "instrumentation-ecosystem" }
              ]
              ${rootName ? "rootProject { name \"${rootName}\" }" : ""}
            }
        """
    }

    private static final String HELLO = '''
        package com.example;
        public class Hello {
            public String greeting() {
                return "hello";
            }
        }
    '''

    def "registers an instrument task only where a source opts in"() {
        given:
        applyEcosystems()
        file('build.gradle.xdcl') << '''
            javaLibrary {
              sources [
                {
                  name "main"
                  instrument {}
                },
                { name "test" }
              ]
            }
        '''

        when:
        succeeds("tasks", "--all")

        then: 'the main source set opted in, so its instrument task is registered'
        outputContains("instrumentMainClasses")
        outputContains("instrument[main]")

        and: 'the test source set did not declare instrument { }, so the reaction skipped it (opt-in)'
        outputDoesNotContain("instrumentTestClasses")
        outputDoesNotContain("instrument[test]")
    }

    def "runs against the compiled classes and produces the instrumented output"() {
        given:
        applyEcosystems("demo")
        file('src/main/java/com/example/Hello.java') << HELLO
        file('build.gradle.xdcl') << '''
            javaLibrary {
              sources [
                {
                  name "main"
                  instrument {}
                }
              ]
            }
        '''

        when: 'the instrument task runs (compiling main first, then copying its bytecode)'
        succeeds("instrumentMainClasses")

        then: 'it ran after the compile task and wrote the instrumented classes to the convention dir'
        executedAndNotSkipped(":compileMainJava", ":instrumentMainClasses")
        file("build/instrumented/main/com/example/Hello.class").assertExists()
    }

    def "honors a custom destinationDir"() {
        given:
        applyEcosystems("demo")
        file('src/main/java/com/example/Hello.java') << HELLO
        file('build.gradle.xdcl') << '''
            javaLibrary {
              sources [
                {
                  name "main"
                  instrument {
                    destinationDir "custom-out"
                  }
                }
              ]
            }
        '''

        when:
        succeeds("instrumentMainClasses")

        then: 'the output went to the declared directory, not the convention one'
        executedAndNotSkipped(":instrumentMainClasses")
        file("custom-out/com/example/Hello.class").assertExists()
        file("build/instrumented/main").assertDoesNotExist()
    }

    def "jar bundles the instrumented classes when the main source opts into instrument"() {
        given:
        applyEcosystems("demo")
        file('src/main/java/com/example/Hello.java') << HELLO
        file('build.gradle.xdcl') << '''
            javaLibrary {
              sources [
                {
                  name "main"
                  instrument {}
                }
              ]
            }
        '''

        when: 'the jar is built'
        succeeds("jar")

        then: 'instrumentation ran as part of building the jar (byteCodeDir was redirected to its output)'
        executedAndNotSkipped(":compileMainJava", ":instrumentMainClasses", ":jar")

        and: 'the jar contains the (instrumented) main class'
        new JarTestFixture(file("build/libs/demo.jar")).assertContainsFile("com/example/Hello.class")
    }

    def "jar bundles the raw compiled classes when no source opts into instrument"() {
        given:
        applyEcosystems("demo")
        file('src/main/java/com/example/Hello.java') << HELLO
        file('build.gradle.xdcl') << '''
            javaLibrary {
              sources [
                { name "main" }
              ]
            }
        '''

        when: 'the jar is built without any instrument { } block'
        succeeds("jar")

        then: 'no instrument task is in the graph — byteCodeDir falls back to its classesDir convention'
        executedAndNotSkipped(":compileMainJava", ":jar")
        !result.executedTasks.contains(":instrumentMainClasses")

        and: 'the jar still contains the compiled main class'
        new JarTestFixture(file("build/libs/demo.jar")).assertContainsFile("com/example/Hello.class")
    }

    def "the ASM instrumentation is actually applied to the bytecode the tests run against"() {
        given: 'a Hello class with a greeting() method, and a test that asserts the injected log line runs'
        applyEcosystems("demo")
        file('src/main/java/com/example/Hello.java') << HELLO
        file('src/test/java/com/example/HelloTest.java') << '''
            package com.example;
            import org.junit.Test;
            import java.io.ByteArrayOutputStream;
            import java.io.PrintStream;
            import static org.junit.Assert.assertTrue;
            public class HelloTest {
                @Test public void greetingIsInstrumented() {
                    PrintStream original = System.out;
                    ByteArrayOutputStream captured = new ByteArrayOutputStream();
                    System.setOut(new PrintStream(captured));
                    try {
                        new Hello().greeting();
                    } finally {
                        System.setOut(original);
                    }
                    assertTrue(
                        "greeting() should print the ASM-injected line, but printed: " + captured,
                        captured.toString().contains("[ASM Injected] Here is a greeting:"));
                }
            }
        '''
        file('build.gradle.xdcl') << """
            javaLibrary {
              sources [
                {
                  name "main"
                  instrument {}
                },
                {
                  name "test"
                  dependencies { implementation [ "junit:junit:4.13.2" ] }
                },
              ]
              repositories [ "${RepoScriptBlockUtil.mavenCentralMirrorUrl}" ]
            }
        """

        when: 'the test runs against the instrumented main classes (test consumes byteCodeDir)'
        succeeds("test")

        then: 'instrumentation ran before the test, and the test compiled and executed against it'
        executedAndNotSkipped(":compileMainJava", ":instrumentMainClasses", ":test")

        and: 'the JUnit result reports the test executed and passed — i.e. the injected log line ran'
        def junitXml = file("build/test-results/test/TEST-com.example.HelloTest.xml")
        junitXml.assertExists()
        junitXml.text.contains('name="greetingIsInstrumented"')
        junitXml.text.contains('tests="1"')
        junitXml.text.contains('failures="0"')
    }
}
