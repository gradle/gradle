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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.AbstractTestReportIntegrationTest
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import static org.hamcrest.CoreMatchers.is

abstract class AbstractJUnit4TestReportIntegrationTest extends AbstractTestReportIntegrationTest implements JUnit4CommonTestSources {
    def "outputs over lifecycle"() {
        // This test checks behavior that was introduced in JUnit 4.13
        Assume.assumeTrue(VersionNumber.parse(version) >= VersionNumber.parse("4.13"))

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
        // Output behavior change in JUnit 4.13
        clazz.assertTestCaseStderr("m1", is("constructor err\nbeforeTest err\nm1 err\nafterTest err\n"))
        clazz.assertTestCaseStderr("m2", is("constructor err\nbeforeTest err\nm2 err\nafterTest err\n"))
        clazz.assertTestCaseStdout("m1", is("constructor out\nbeforeTest out\nm1 out\nafterTest out\n"))
        clazz.assertTestCaseStdout("m2", is("constructor out\nbeforeTest out\nm2 out\nafterTest out\n"))
        clazz.assertStderr(is("beforeClass err\nafterClass err\n"))
        clazz.assertStdout(is("beforeClass out\nafterClass out\n"))
    }
}
