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
import org.gradle.testing.junit.AbstractJUnitLoggingOutputCaptureIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER
import static org.hamcrest.CoreMatchers.containsString

// https://github.com/junit-team/junit5/issues/1285
@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterLoggingOutputCaptureIntegrationTest extends AbstractJUnitLoggingOutputCaptureIntegrationTest implements JUnitJupiterMultiVersionTest {
    def "captures logging output events"() {
        file("src/test/java/OkTest.java") << """
            ${testFrameworkImports}

            public class OkTest {
                static {
                    System.out.println("class loaded");
                }

                public OkTest() {
                    System.out.println("test constructed");
                }

                ${beforeClassAnnotation} public static void init() {
                    System.out.println("before class out");
                    System.err.println("before class err");
                }

                ${afterClassAnnotation} public static void end() {
                    System.out.println("after class out");
                    System.err.println("after class err");
                }

                ${beforeTestAnnotation}
                public void before() {
                    System.out.println("before out");
                    System.err.println("before err");
                }

                ${afterTestAnnotation}
                public void after() {
                    System.out.println("after out");
                    System.err.println("after err");
                }

                @Test
                public void ok() {
                    System.out.print("test out: \u03b1</html>");
                    System.out.println();
                    System.err.println("test err");
                }

                @Test
                public void anotherOk() {
                    System.out.println("ok out");
                    System.err.println("ok err");
                }
            }
        """.stripIndent()

        when:
        succeeds "test"

        then:

        outputContains(
            "Test class OkTest -> class loaded\n" +
            "Test class OkTest -> before class out\n" +
            "Test class OkTest -> before class err\n" +
            "Test class OkTest -> test constructed\n" +
            "Test anotherOk(OkTest) -> before out\n" +
            "Test anotherOk(OkTest) -> before err\n" +
            "Test anotherOk(OkTest) -> ok out\n" +
            "Test anotherOk(OkTest) -> ok err\n" +
            "Test anotherOk(OkTest) -> after out\n" +
            "Test anotherOk(OkTest) -> after err\n" +
            "Test class OkTest -> test constructed\n" +
            "Test ok(OkTest) -> before out\n" +
            "Test ok(OkTest) -> before err\n" +
            "Test ok(OkTest) -> test out: \u03b1</html>\n" +
            "Test ok(OkTest) -> test err\n" +
            "Test ok(OkTest) -> after out\n" +
            "Test ok(OkTest) -> after err\n" +
            "Test class OkTest -> after class out\n" +
            "Test class OkTest -> after class err\n"
        )

        // This test covers current behaviour, not necessarily desired behaviour

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = xmlReport.testClass("OkTest")
        classResult.assertTestCaseStdout("ok", containsString(
            "before out\n" +
            "test out: \u03b1</html>\n" +
            "after out\n"
        ))
        classResult.assertTestCaseStderr("ok", containsString(
            "before err\n" +
            "test err\n" +
            "after err\n"
        ))
        classResult.assertStdout(containsString(
            "class loaded\n" +
            "before class out\n" +
            "test constructed\n" +
            "test constructed\n" +
            "after class out\n"
        ))
        classResult.assertStderr(containsString(
            "before class err\n" +
            "after class err\n"
        ))


        def htmlReport = new HtmlTestExecutionResult(testDirectory)
        def classReport = htmlReport.testClass("OkTest")
        classReport.assertStdout(containsString(
            "class loaded\n" +
            "before class out\n" +
            "test constructed\n" +
            "before out\n" +
            "ok out\n" +
            "after out\n" +
            "test constructed\n" +
            "before out\n" +
            "test out: \u03b1</html>\n" +
            "after out\n" +
            "after class out\n"
        ))
        classReport.assertStderr(containsString(
            "before class err\n" +
            "before err\n" +
            "ok err\n" +
            "after err\n" +
            "before err\n" +
            "test err\n" +
            "after err\n" +
            "after class err\n"
        ))
    }
}
