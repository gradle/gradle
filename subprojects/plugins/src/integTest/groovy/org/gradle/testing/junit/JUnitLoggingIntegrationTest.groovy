/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.util.TextUtil
import org.junit.Rule
import org.junit.Test
import org.gradle.integtests.fixtures.JUnitTestExecutionResult

import static org.hamcrest.Matchers.equalTo

// cannot make assumptions about order in which test methods of JUnit4Test get executed
class JUnitLoggingIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources
    ExecutionResult result

    def setup() {
        executer.setAllowExtraLogging(false).withStackTraceChecksDisabled().withTasks("test")
    }

    def "defaultLifecycleLogging"() {
        when:
        result = executer.runWithFailure()

        then:
        outputContains("""
org.gradle.JUnit4Test > badTest FAILED
    java.lang.RuntimeException at JUnit4Test.groovy:44
        """)
    }

    def "customQuietLogging"() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
badTest FAILED
    java.lang.RuntimeException: bad
        at org.gradle.JUnit4Test.beBad(JUnit4Test.groovy:44)
        at org.gradle.JUnit4Test.badTest(JUnit4Test.groovy:28)
        """)

        outputContains("ignoredTest SKIPPED")

        outputContains("org.gradle.JUnit4Test FAILED")
    }

    def "standardOutputLogging"() {
        when:
        result = executer.withArguments("-q").runWithFailure()

        then:
        outputContains("""
org.gradle.JUnit4StandardOutputTest > printTest STANDARD_OUT
    line 1
    line 2
    line 3
        """)
    }

    @Test
    void "test logging is included in XML results"() {
        file("build.gradle") << """
            apply plugin: 'java'
                repositories { mavenCentral() }
                dependencies { testCompile 'junit:junit:4.11' }
        """

        file("src/test/java/EncodingTest.java") << """
import org.junit.*;

public class EncodingTest {
    @Test public void encodesCdata() {
        System.out.println("< html allowed, cdata closing token ]]> encoded!");
        System.out.print("no EOL, ");
        System.out.println("non-asci char: ż");
        System.out.println("xml entity: &amp;");
        System.err.println("< html allowed, cdata closing token ]]> encoded!");
    }
    @Test public void encodesAttributeValues() {
        throw new RuntimeException("html: <> cdata: ]]>");
    }
}
"""
        when:
        executer.withTasks("test").runWithFailure()

        then:
        new JUnitTestExecutionResult(testDir)
                .testClass("EncodingTest")
                .assertTestPassed("encodesCdata")
                .assertTestFailed("encodesAttributeValues", equalTo('java.lang.RuntimeException: html: <> cdata: ]]>'))
                .assertStdout(equalTo("""< html allowed, cdata closing token ]]> encoded!
no EOL, non-asci char: ż
xml entity: &amp;
"""))
                .assertStderr(equalTo("< html allowed, cdata closing token ]]> encoded!\n"))
    }


    private void outputContains(String text) {
        assert result.output.contains(TextUtil.toPlatformLineSeparators(text.trim()))
    }
}
