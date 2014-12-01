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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HtmlTestExecutionResult
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.testing.fixture.JUnitCoverage
import spock.lang.Ignore

import static org.hamcrest.Matchers.is

class JUnitLoggingOutputCaptureIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: "java"
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:$JUnitCoverage.NEWEST' }
            test {
                reports.junitXml.outputPerTestCase = true
                onOutput { test, event -> print "\$test -> \$event.message" }
            }
        """
    }

    def "captures logging output events"() {
        file("src/test/java/OkTest.java") << """
public class OkTest {
    static {
        System.out.println("class loaded");
    }

    public OkTest() {
        System.out.println("test constructed");
    }

    @org.junit.BeforeClass public static void init() {
        System.out.println("before class out");
        System.err.println("before class err");
    }

    @org.junit.AfterClass public static void end() {
        System.out.println("after class out");
        System.err.println("after class err");
    }

    @org.junit.Before
    public void before() {
        System.out.println("before out");
        System.err.println("before err");
    }

    @org.junit.After
    public void after() {
        System.out.println("after out");
        System.err.println("after err");
    }

    @org.junit.Test
    public void ok() {
        System.out.println("test out");
        System.err.println("test err");
    }
}
"""
        when: run "test"

        then:
        result.output.contains("""test class OkTest -> class loaded
test class OkTest -> before class out
test class OkTest -> before class err
test class OkTest -> test constructed
test ok(OkTest) -> before out
test ok(OkTest) -> before err
test ok(OkTest) -> test out
test ok(OkTest) -> test err
test ok(OkTest) -> after out
test ok(OkTest) -> after err
test class OkTest -> after class out
test class OkTest -> after class err
""")

        // This test covers current behaviour, not necessarily desired behaviour

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def classResult = xmlReport.testClass("OkTest")
        classResult.assertTestCaseStdout("ok", is("""before out
test out
after out
"""))
        classResult.assertTestCaseStderr("ok", is("""before err
test err
after err
"""))
        classResult.assertStdout(is("""class loaded
before class out
test constructed
after class out
"""))
        classResult.assertStderr(is("""before class err
after class err
"""))

        def htmlReport = new HtmlTestExecutionResult(testDirectory)
        def classReport = htmlReport.testClass("OkTest")
        classReport.assertStdout(is("""class loaded
before class out
test constructed
before out
test out
after out
after class out
"""))
        classReport.assertStderr(is("""before class err
before err
test err
after err
after class err
"""))
    }

    @Ignore
    def "captures output from logging frameworks"() {
        expect: false
    }

    @Ignore
    def "test can generate output from multiple threads"() {
        expect: false
    }

    @Ignore
    def "output does not require trailing end-of-line separator"() {
        expect: false
    }
}
