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

package org.gradle.testing.junit.platform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.fixture.JUnitCoverage
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION

@Issue("https://github.com/gradle/gradle/issues/26177")
class JUnitPlatformInitFailureConsoleLoggingIntegrationTest extends AbstractIntegrationSpec {
    def "framework initialization failure is logged to console under default granularity"() {
        // A class-level @BeforeAll lifecycle failure aborts the FooTest container before any test
        // method runs. The Jupiter engine reports executionFinished(container, FAILED), which is
        // routed by JUnitPlatformTestExecutionListener.executionFinished (testIdentifier.isTest()
        // == false branch) through createSyntheticTestDescriptorForContainer. That synthesizes a
        // leaf "initializationError" descriptor under the failing class container (named
        // "initializationError" rather than "executionError" because no descendant tests have
        // started yet) and attaches the failure to that leaf via TestFailure.fromTestFrameworkFailure.
        // Under default granularity (minGranularity = -1, leaves only) the synthetic leaf is shown
        // directly — its failure surfaces in the console without needing the framework-failure or
        // composite-own-failure bypass to fire. This pins down the user-visible behavior for the
        // issue-26177 class-initialisation scenario.
        //
        // Note: a per-method ctor throw is NOT the right reproducer here. Jupiter's default
        // Lifecycle.PER_METHOD attributes such failures to the individual @Test method (the
        // existing leaf), which surfaces as "FooTest > foo() FAILED" via reportTestFailure —
        // a different code path that doesn't exercise the synthetic-leaf machinery.
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test { useJUnitPlatform() }
        """
        file("src/test/java/FooTest.java") << """
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.Test;

            public class FooTest {
                @BeforeAll
                public static void boom() { throw new NullPointerException("boom from @BeforeAll"); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        // Default TestExceptionFormat is SHORT. Asserting on the synthesized leaf event line and
        // the exception class name is sufficient to prove the failure reaches the console.
        outputContains("FooTest > initializationError FAILED")
        outputContains("java.lang.NullPointerException")
    }

    def "framework initialization failure is logged under explicit non-default granularity"() {
        // minGranularity = 0, maxGranularity = 1 keeps only task/process-level events.
        // The synthesized "initializationError" leaf lives below the engine + class
        // composites (≥ method level), so without the framework-failure classification at
        // JUnitPlatformTestExecutionListener's container-failure branch the leaf gets
        // filtered and the user sees only "> Task :test FAILED". With the bypass, the
        // framework-failure flag overrides the granularity gate and the failure surfaces.
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test {
                useJUnitPlatform()
                testLogging {
                    minGranularity = 0
                    maxGranularity = 1
                    showExceptions = true
                }
            }
        """
        file("src/test/java/FooTest.java") << """
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.Test;

            public class FooTest {
                @BeforeAll
                public static void boom() { throw new NullPointerException("boom from @BeforeAll"); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        outputContains("FooTest > initializationError FAILED")
        outputContains("java.lang.NullPointerException")
    }

    def "framework initialization failure via Assertions.fail bypasses granularity"() {
        // Regression check for the mapper-claim hazard: a @BeforeAll that uses Jupiter's
        // Assertions.fail(...) throws AssertionFailedError. Without the
        // DefaultThrowableToTestFailureMapper short-circuit (which skips the mapper chain
        // when isFailureDuringTest=false), the AssertionFailureMapper would claim the
        // throwable and emit fromTestAssertionFailure → plain AssertionFailureDetails →
        // NOT bypass-eligible. With the short-circuit, the container-level failure routes
        // straight to fromTestFrameworkFailure regardless of the throwable's type, so it
        // bypasses the granularity gate.
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test {
                useJUnitPlatform()
                testLogging {
                    minGranularity = 0
                    maxGranularity = 1
                    showExceptions = true
                }
            }
        """
        file("src/test/java/FooTest.java") << """
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.Test;

            public class FooTest {
                @BeforeAll
                public static void boom() { Assertions.fail("assertions-fail-from-@BeforeAll"); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        outputContains("FooTest > initializationError FAILED")
        outputContains("org.opentest4j.AssertionFailedError")
    }

    @Issue("https://github.com/gradle/gradle/issues/26177")
    static class JUnitPlatformOrdinaryFailuresArentMisclassifiedIntegrationTest extends AbstractIntegrationSpec {
        private void writeProject(String testBody) {
            buildFile << """
                plugins {
                    id("java-library")
                }

                ${mavenCentralRepository()}

                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:${JUnitCoverage.LATEST_JUPITER_VERSION}'
                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                }

                test {
                    useJUnitPlatform()
                    testLogging {
                        // Keep only task/process-level events; method-level (leaf) events are filtered.
                        minGranularity = 0
                        maxGranularity = 1
                        showExceptions = true
                    }
                }
            """

            file("src/test/java/FooTest.java") << """
                import org.junit.jupiter.api.Test;

                public class FooTest {
                    @Test public void foo() { ${testBody} }
                }
            """
        }

        def "ordinary non-assertion in-body failure does not bypass the granularity filter"() {
            writeProject('throw new IllegalStateException("ordinary in-body boom");')

            expect:
            fails("test")

            outputDoesNotContain("FooTest > foo() FAILED")
            outputDoesNotContain("java.lang.IllegalStateException")
        }

        def "control: ordinary assertion failure is correctly filtered by the granularity config"() {
            writeProject('throw new AssertionError("ordinary assertion boom");')

            expect:
            fails("test")

            and:
            // Assertion failures do not appear (as expected)
            outputDoesNotContain("FooTest > foo() FAILED")
            outputDoesNotContain("ordinary assertion boom")
        }
    }
}
