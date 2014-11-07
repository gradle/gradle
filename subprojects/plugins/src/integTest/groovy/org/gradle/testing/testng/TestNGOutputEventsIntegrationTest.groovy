/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.fixture.TestNGCoverage

import static org.hamcrest.Matchers.is

class TestNGOutputEventsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java"
            repositories { jcenter() }
            dependencies { testCompile "org.testng:testng:$TestNGCoverage.NEWEST" }
            test.useTestNG()
            test.reports.junitXml.outputPerTestCase = true
        """

        file("src/test/java/FooTest.java") << """import org.testng.annotations.*;
            public class FooTest {
                public FooTest() {
                    System.out.println("constructor out"); System.err.println("constructor err");
                }
                @BeforeClass public static void beforeClass() {
                    System.out.println("beforeClass out"); System.err.println("beforeClass err");
                }
                @BeforeTest public void beforeTest() {
                    System.out.println("beforeTest out"); System.err.println("beforeTest err");
                }
                @Test public void m1() {
                    System.out.println("m1 out"); System.err.println("m1 err");
                }
                @Test public void m2() {
                    System.out.println("m2 out"); System.err.println("m2 err");
                }
                @AfterTest public void afterTest() {
                    System.out.println("afterTest out"); System.err.println("afterTest err");
                }
                @AfterClass public static void afterClass() {
                    System.out.println("afterClass out"); System.err.println("afterClass err");
                }
            }
        """
    }

    def "attaches events to correct test descriptors of a suite"() {
        buildFile << "test.useTestNG { suites 'suite.xml' }"

        file("suite.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="AwesomeSuite">
  <test name='The Foo Test'><classes><class name='FooTest'/></classes></test>
</suite>"""

        when: run "test"

        then:
        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("FooTest")

        /**
         * This test documents the current behavior. It's not right, we're missing a lot of output in the report.
         */

        clazz.assertTestCaseStderr("m1", is("m1 err\n"))
        clazz.assertTestCaseStderr("m2", is("m2 err\n"))
        clazz.assertTestCaseStdout("m1", is("m1 out\n"))
        clazz.assertTestCaseStdout("m2", is("m2 out\n"))
        clazz.assertStderr(is(""))
        clazz.assertStdout(is(""))
    }

    def "attaches output events to correct test descriptors"() {
        when: run "test"

        then:
        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("FooTest")

        /**
         * This test documents the current behavior. It's not right, we're missing a lot of output in the report.
         */

        clazz.assertTestCaseStderr("m1", is("m1 err\n"))
        clazz.assertTestCaseStderr("m2", is("m2 err\n"))
        clazz.assertTestCaseStdout("m1", is("m1 out\n"))
        clazz.assertTestCaseStdout("m2", is("m2 out\n"))
        clazz.assertStderr(is(""))
        clazz.assertStdout(is(""))
    }
}
