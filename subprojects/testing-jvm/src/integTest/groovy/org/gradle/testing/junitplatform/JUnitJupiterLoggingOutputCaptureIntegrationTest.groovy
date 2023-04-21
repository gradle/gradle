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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.AbstractJUnitLoggingOutputCaptureIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER
import static org.hamcrest.CoreMatchers.is

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

        outputContains """
            Test class OkTest -> class loaded
            Test class OkTest -> before class out
            Test class OkTest -> before class err
            Test class OkTest -> test constructed
            Test anotherOk(OkTest) -> before out
            Test anotherOk(OkTest) -> before err
            Test anotherOk(OkTest) -> ok out
            Test anotherOk(OkTest) -> ok err
            Test anotherOk(OkTest) -> after out
            Test anotherOk(OkTest) -> after err
            Test class OkTest -> test constructed
            Test ok(OkTest) -> before out
            Test ok(OkTest) -> before err
            Test ok(OkTest) -> test out: \u03b1</html>
            Test ok(OkTest) -> test err
            Test ok(OkTest) -> after out
            Test ok(OkTest) -> after err
            Test class OkTest -> after class out
            Test class OkTest -> after class err
        """.stripIndent().stripLeading()

        // This test covers current behaviour, not necessarily desired behaviour

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = xmlReport.testClass("OkTest")
        classResult.assertTestCaseStdout("ok", is("""
            before out
            test out: \u03b1</html>
            after out
        """.stripIndent().stripLeading()))
        classResult.assertTestCaseStderr("ok", is("""
            before err
            test err
            after err
        """.stripIndent().stripLeading()))
        classResult.assertStdout(is("""
            class loaded
            before class out
            test constructed
            test constructed
            after class out
        """.stripIndent().stripLeading()))
        classResult.assertStderr(is("""
            before class err
            after class err
        """.stripIndent().stripLeading()))


        def htmlReport = new HtmlTestExecutionResult(testDirectory)
        def classReport = htmlReport.testClass("OkTest")
        classReport.assertStdout(is("""
            class loaded
            before class out
            test constructed
            before out
            ok out
            after out
            test constructed
            before out
            test out: \u03b1</html>
            after out
            after class out
        """.stripIndent().stripLeading()))
        classReport.assertStderr(is("""
            before class err
            before err
            ok err
            after err
            before err
            test err
            after err
            after class err
        """.stripIndent().stripLeading()))
    }
}
