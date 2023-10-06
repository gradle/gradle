/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.AbstractTestReportIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterTestReportIntegrationTest extends AbstractTestReportIntegrationTest implements JUnitJupiterMultiVersionTest {
    def "outputs over lifecycle"() {
        when:
        buildScript """
            $junitSetup
            test.reports.junitXml.outputPerTestCase = true
        """

        file("src/test/java/OutputLifecycleTest.java") << """
            ${testFrameworkImports}

            public class OutputLifecycleTest {

                public OutputLifecycleTest() {
                    System.out.println("constructor out");
                    System.err.println("constructor err");
                }

                ${beforeClassAnnotation}
                public static void beforeClass() {
                    System.out.println("beforeClass out");
                    System.err.println("beforeClass err");
                }

                ${beforeTestAnnotation}
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

                ${afterTestAnnotation}
                public void afterTest() {
                    System.out.println("afterTest out");
                    System.err.println("afterTest err");
                }

                ${afterClassAnnotation}
                public static void afterClass() {
                    System.out.println("afterClass out");
                    System.err.println("afterClass err");
                }
            }
        """

        succeeds "test"

        then:
        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("OutputLifecycleTest")
        clazz.assertTestCaseStderr("m1", is("beforeTest err\nm1 err\nafterTest err\n"))
        clazz.assertTestCaseStderr("m2", is("beforeTest err\nm2 err\nafterTest err\n"))
        clazz.assertTestCaseStdout("m1", is("beforeTest out\nm1 out\nafterTest out\n"))
        clazz.assertTestCaseStdout("m2", is("beforeTest out\nm2 out\nafterTest out\n"))
        clazz.assertStderr(is("beforeClass err\nconstructor err\nconstructor err\nafterClass err\n"))
        clazz.assertStdout(is("beforeClass out\nconstructor out\nconstructor out\nafterClass out\n"))
    }

    def "collects output for failing non-root suite descriptors"() {
        given:
        buildScript """
            $junitSetup
            dependencies {
                testImplementation(platform('org.junit:junit-bom:$version'))
                testImplementation('org.junit.platform:junit-platform-launcher')
            }
        """

        and:
        testClass "SomeTest"
        file("src/test/java/ThrowingListener.java") << """
            import org.junit.platform.launcher.*;
            public class ThrowingListener implements TestExecutionListener {
                @Override
                public void testPlanExecutionStarted(TestPlan testPlan) {
                    System.out.println("System.out from ThrowingListener");
                    System.err.println("System.err from ThrowingListener");
                    throw new OutOfMemoryError("not caught by JUnit Platform");
                }
            }
        """
        file("src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener") << "ThrowingListener"

        when:
        fails "test"

        then:
        new HtmlTestExecutionResult(testDirectory)
            .testClassStartsWith("Gradle Test Executor")
            .assertTestFailed("failed to execute tests", containsString("Could not complete execution"))
            .assertStdout(containsString("System.out from ThrowingListener"))
            .assertStderr(containsString("System.err from ThrowingListener"))
    }
}
