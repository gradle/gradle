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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/26177")
class TestNGInitFailureConsoleLoggingIntegrationTest extends AbstractIntegrationSpec {
    def "framework initialization failure is logged to console under default granularity"() {
        TestNGCoverage.enableTestNG(buildFile, '6.3.1')
        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                public FooTest() { throw new NullPointerException("boom from ctor"); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        // With default testLogging (minGranularity=-1), the framework-initialization
        // failure is attached to a composite descriptor and currently gets filtered
        // out by TestEventLogger.isLoggedGranularity — so the user sees only
        // "> There were failing tests" and has to hunt down the XML report.
        // After the fix, fatal/initialization failures should bypass granularity
        // filtering and surface the failing exception in the console output.
        outputContains("NullPointerException")
    }

    def "framework initialization failure is logged under explicit non-default granularity"() {
        TestNGCoverage.enableTestNG(buildFile, '6.3.1')
        // Explicit testLogging that would normally filter composite (class/suite) descriptors:
        // minGranularity=3 (methods+) and the TestNG framework-failure failure lands on the
        // class-level descriptor (level 2), so without the bypass the failure would be filtered.
        buildFile << """
            test {
                testLogging {
                    minGranularity = 3
                    maxGranularity = -1
                    showExceptions = true
                }
            }
        """
        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                public FooTest() { throw new NullPointerException("boom from ctor"); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        // The bypass should make framework-failure failures visible even when the
        // configured granularity would otherwise filter the composite class-level event.
        outputContains("NullPointerException")
    }

    @Issue("https://github.com/gradle/gradle/issues/26177")
    static class TestNGOrdinaryFailuresArentMisclassifiedIntegrationTest extends AbstractIntegrationSpec {
        private void writeProject(String testBody) {
            TestNGCoverage.enableTestNG(buildFile, '6.3.1')
            buildFile << """
                test {
                    testLogging {
                        // Keep only task/process-level events; method-level (leaf) events are filtered.
                        minGranularity = 0
                        maxGranularity = 1
                        showExceptions = true
                    }
                }
            """

            file("src/test/java/FooTest.java") << """
                import org.testng.annotations.*;

                public class FooTest {
                    @Test public void foo() { ${testBody} }
                }
            """
        }

        def "ordinary non-assertion in-body failure does not bypass the granularity filter"() {
            // After the polarity inversion, ordinary in-test-method throwables route through
            // fromTestMethodFailure (plain DefaultTestFailureDetails) — NOT bypass-eligible.
            // Asserting on the absence of the per-method FAILED line under maxGranularity=1
            // proves the polarity inversion does not over-classify ordinary test failures as
            // framework failures.
            writeProject('throw new IllegalStateException("ordinary in-body boom");')

            expect:
            fails("test")

            outputDoesNotContain("FooTest.foo FAILED")
            outputDoesNotContain("java.lang.IllegalStateException")
        }

        def "control: ordinary assertion failure is correctly filtered by the granularity config"() {
            writeProject('throw new AssertionError("ordinary assertion boom");')

            expect:
            fails("test")

            and:
            // Assertion failures do not appear (as expected): the per-method FAILED line is
            // filtered by maxGranularity=1 and the assertion path is not bypass-eligible.
            outputDoesNotContain("FooTest.foo FAILED")
            outputDoesNotContain("ordinary assertion boom")
        }
    }
}
