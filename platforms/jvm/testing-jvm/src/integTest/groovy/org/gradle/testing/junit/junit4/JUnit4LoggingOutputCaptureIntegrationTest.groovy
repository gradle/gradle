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

import groovy.xml.XmlParser
import org.gradle.integtests.fixtures.TargetCoverage

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4

@TargetCoverage({ JUNIT_4 })
class JUnit4LoggingOutputCaptureIntegrationTest extends AbstractJUnit4LoggingOutputCaptureIntegrationTest implements JUnit4MultiVersionTest {
    def "can configure logging output inclusion in xml reports"() {
        given:
        buildFile.text = buildFile.text.replace("reports.junitXml.outputPerTestCase = true", """reports.junitXml {
            outputPerTestCase = true
            $includeSystemOut
            $includeSystemErr
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

        and:
        def xmlResult = file("build/test-results/test/TEST-OkTest.xml")
        def doc = new XmlParser().parseText(xmlResult.text)

        and: "suite level output (before/after class) is included/excluded in the xml report as configured"
        def testSuiteOutput = doc.tap {
            assert it.name() == 'testsuite'
            assert it["@name"] == 'OkTest'
        }
        def suiteStandardOut = testSuiteOutput.'system-out'.text()
        def suiteStandardErr = testSuiteOutput.'system-err'.text()
        suiteStandardOut.isEmpty() == !standardOutIncluded
        suiteStandardErr.isEmpty() == !standardErrIncluded

        and: "test method output (includes setup/teardown) is included/excluded in the xml report as configured"
        def testMethodOutput = testSuiteOutput.'testcase'.find { it.@classname = 'OkTest' && it.@name == 'isOk' }
        def testStandardOut = testMethodOutput.'system-out'.text()
        def testStandardErr = testMethodOutput.'system-err'.text()
        testStandardOut.isEmpty() == !standardOutIncluded
        testStandardErr.isEmpty() == !standardErrIncluded

        and: "all output appeared in the console when running tests"
        outputContains("before class output")
        outputContains("test method output")
        result.assertHasErrorOutput("before class error")
        result.assertHasErrorOutput("test method error")

        where:
        includeSystemOut                    | includeSystemErr                  || standardOutIncluded || standardErrIncluded
        "// default includeSystemOutLog"    | "// default includeSystemErrLog"  || true                || true
        "includeSystemOutLog = true"        | "includeSystemErrLog = true"      || true                || true
        "includeSystemOutLog = false"       | "includeSystemErrLog = true"      || false               || true
        "includeSystemOutLog = true"        | "includeSystemErrLog = false"     || true                || false
        "includeSystemOutLog = false"       | "includeSystemErrLog = false"     || false               || false
    }
}
