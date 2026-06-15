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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/26177")
class JUnit4InitFailureConsoleLoggingIntegrationTest extends AbstractIntegrationSpec {
    private static final String JUNIT4 = '4.13.2'

    /**
     * Writes a JUnit 4 test class whose @RunWith points to a custom Runner whose run() method
     * unconditionally throws. This routes through JUnitTestExecutor.accept's catch block with
     * started=true, hitting the line-92 path which (post-fix) emits the failure via
     * fromTestFrameworkFailure → TestFrameworkFailureDetails → granularity bypass.
     */
    private void writeBrokenRunnerTestProject() {
        file("src/test/java/BrokenRunner.java") << """
            import org.junit.runner.Description;
            import org.junit.runner.Runner;
            import org.junit.runner.notification.RunNotifier;

            public class BrokenRunner extends Runner {
                private final Class<?> type;
                public BrokenRunner(Class<?> type) { this.type = type; }
                @Override public Description getDescription() { return Description.createSuiteDescription(type); }
                @Override public void run(RunNotifier notifier) { throw new RuntimeException("brokenRunnerBoom"); }
            }
        """
        file("src/test/java/FooTest.java") << """
            import org.junit.*;
            import org.junit.runner.RunWith;

            @RunWith(BrokenRunner.class)
            public class FooTest {
                @Test public void foo() {}
            }
        """
    }

    def "framework initialization failure is logged to console under default granularity"() {
        // JUnit 4 currently synthesizes a leaf "executionError" descriptor for line-92 failures
        // via JUnitTestEventAdapter.testExecutionFailure (analogous to the JUnit Platform
        // synthetic-leaf trick). Under default granularity (leaves only) the synthetic leaf
        // already shows — this test serves as a regression check that the JUnitTestExecutor
        // normalization (line 92 now emits a framework-failure failure) does not break
        // existing behavior.
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:${JUNIT4}' }
            test { useJUnit() }
        """
        writeBrokenRunnerTestProject()

        expect:
        fails("test")

        // The failure message from BrokenRunner.run should appear in the console.
        // Default TestExceptionFormat is SHORT — shows "<ExceptionClass> at <File>:<line>"
        // (the message string is intentionally omitted in the short format). Asserting on the
        // event line + exception class is sufficient to prove the failure reaches the console.
        outputContains("FooTest > executionError FAILED")
        outputContains("java.lang.RuntimeException")
    }

    def "framework initialization failure is logged under explicit non-default granularity"() {
        // minGranularity=0, maxGranularity=1 keeps only task/process events.
        // The synthetic "executionError" leaf is at method level (≥2) and is normally
        // filtered. With the bypass, the framework-failure flag overrides the granularity
        // gate and the failure surfaces anyway.
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:${JUNIT4}' }
            test {
                useJUnit()
                testLogging {
                    minGranularity = 0
                    maxGranularity = 1
                    showExceptions = true
                }
            }
        """
        writeBrokenRunnerTestProject()

        expect:
        fails("test")

        // Default TestExceptionFormat is SHORT — shows "<ExceptionClass> at <File>:<line>"
        // (the message string is intentionally omitted in the short format). Asserting on the
        // event line + exception class is sufficient to prove the failure reaches the console.
        outputContains("FooTest > executionError FAILED")
        outputContains("java.lang.RuntimeException")
    }

    /**
     * Writes a JUnit 4 test class whose @RunWith points to a custom Suite subclass whose run()
     * method unconditionally throws. Mirrors {@link #writeBrokenRunnerTestProject()} but the
     * thrower is a {@link org.junit.runners.Suite} rather than a bare {@link org.junit.runner.Runner},
     * exercising the same JUnitTestExecutor.accept catch (started=true) path through a
     * composite/suite-style runner.
     */
    private void writeBrokenSuiteTestProject() {
        file("src/test/java/BrokenSuite.java") << """
            import org.junit.runner.notification.RunNotifier;
            import org.junit.runners.Suite;
            import org.junit.runners.model.InitializationError;

            public class BrokenSuite extends Suite {
                public BrokenSuite(Class<?> klass) throws InitializationError {
                    super(klass, new Class<?>[0]);
                }
                @Override public void run(RunNotifier notifier) { throw new RuntimeException("brokenSuiteBoom"); }
            }
        """
        file("src/test/java/BarTest.java") << """
            import org.junit.*;
            import org.junit.runner.RunWith;

            @RunWith(BrokenSuite.class)
            public class BarTest {
                @Test public void bar() {}
            }
        """
    }

    def "framework initialization failure from custom Suite runner is logged to console under default granularity"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:${JUNIT4}' }
            test { useJUnit() }
        """
        writeBrokenSuiteTestProject()

        expect:
        fails("test")

        outputContains("BarTest > executionError FAILED")
        outputContains("java.lang.RuntimeException")
    }

    def "framework initialization failure from custom Suite runner is logged under explicit non-default granularity"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:${JUNIT4}' }
            test {
                useJUnit()
                testLogging {
                    minGranularity = 0
                    maxGranularity = 1
                    showExceptions = true
                }
            }
        """
        writeBrokenSuiteTestProject()

        expect:
        fails("test")

        outputContains("BarTest > executionError FAILED")
        outputContains("java.lang.RuntimeException")
    }
}
