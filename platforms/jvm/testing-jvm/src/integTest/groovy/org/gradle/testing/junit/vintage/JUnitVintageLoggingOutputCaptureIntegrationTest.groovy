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

package org.gradle.testing.junit.vintage

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.junit4.AbstractJUnit4LoggingOutputCaptureIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE

// https://github.com/junit-team/junit5/issues/1285
@TargetCoverage({ JUNIT_VINTAGE })
class JUnitVintageLoggingOutputCaptureIntegrationTest extends AbstractJUnit4LoggingOutputCaptureIntegrationTest implements JUnitVintageMultiVersionTest {
    def "can configure logging output inclusion in xml reports"() {
        given:
        buildFile.text = buildFile.text.replace("reports.junitXml.outputPerTestCase = true", """reports.junitXml {
            outputPerTestCase = true
            $includeSystemOutConf
            $includeSystemErrConf
        }""".stripIndent())

        file("src/test/java/OkTest.java") << """
            ${testFrameworkImports}

            public class OkTest {
                @BeforeClass
                public static void init() {
                    System.out.println("before class output");
                    System.err.println("before class error");
                }

                @Test
                public void isOk() {
                    System.out.println("test method output");
                    System.err.println("test method error");
                }
            }
        """

        expect:
        executer.withTestConsoleAttached()
        succeeds("test")

        and: "all output is included/excluded in the xml report as configured"
        def junitResult = new JUnitXmlTestExecutionResult(testDirectory)
        if (standardOutIncluded) {
            assert junitResult.getSuiteStandardOutput("OkTest").isPresent()
            assert junitResult.getTestCaseStandardOutput("OkTest", "isOk").isPresent()
        } else {
            assert !junitResult.getSuiteStandardOutput("OkTest").isPresent() // isEmpty not available in Java 8
            assert !junitResult.getTestCaseStandardOutput("OkTest", "isOk").isPresent()
        }
        if (standardErrIncluded) {
            assert junitResult.getSuiteStandardError("OkTest").isPresent()
            assert junitResult.getTestCaseStandardError("OkTest", "isOk").isPresent()
        } else {
            assert !junitResult.getSuiteStandardError("OkTest").isPresent()
            assert !junitResult.getTestCaseStandardError("OkTest", "isOk").isPresent()
        }

        and: "all output appeared in the console when running tests"
        outputContains("before class output")
        outputContains("test method output")
        result.assertHasErrorOutput("before class error")
        result.assertHasErrorOutput("test method error")

        where:
        includeSystemOutConf                | includeSystemErrConf              || standardOutIncluded || standardErrIncluded
        "// default includeSystemOutLog"    | "// default includeSystemErrLog"  || true                || true
        "includeSystemOutLog = true"        | "includeSystemErrLog = true"      || true                || true
        "includeSystemOutLog = false"       | "includeSystemErrLog = true"      || false               || true
        "includeSystemOutLog = true"        | "includeSystemErrLog = false"     || true                || false
        "includeSystemOutLog = false"       | "includeSystemErrLog = false"     || false               || false
    }
}
