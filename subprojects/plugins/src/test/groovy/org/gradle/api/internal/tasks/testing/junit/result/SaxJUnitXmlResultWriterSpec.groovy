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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.JavaVersion
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.integtests.fixtures.JUnitTestClassExecutionResult
import spock.lang.Shared
import spock.lang.Specification

import javax.xml.stream.XMLOutputFactory

import static java.util.Arrays.asList
import static java.util.Collections.emptyList
import static org.gradle.api.tasks.testing.TestResult.ResultType.*
import static org.hamcrest.Matchers.equalTo

/**
 * by Szczepan Faber, created at: 11/16/12
 */
class SaxJUnitXmlResultWriterSpec extends Specification {

    private provider = Mock(TestResultsProvider)
    private generator = new SaxJUnitXmlResultWriter("localhost", provider, XMLOutputFactory.newInstance())

    // XMLStreamWriter uses single quotes in the <xml> leading element on java 5
    @Shared startQuoteChar = JavaVersion.current().java5 ? "'" : '"'
    private startTime = 1353344968049

    def "writes xml JUnit result"() {
        StringWriter sw = new StringWriter()
        TestClassResult result = new TestClassResult(startTime)
        result.add(new TestMethodResult("some test", new DefaultTestResult(SUCCESS, startTime + 10, startTime + 25, 1, 1, 0, emptyList())))
        result.add(new TestMethodResult("some test two", new DefaultTestResult(SUCCESS, startTime + 15, startTime + 30, 1, 1, 0, emptyList())))
        result.add(new TestMethodResult("some failing test", new DefaultTestResult(FAILURE, startTime + 30, startTime + 40, 1, 0, 1, [new RuntimeException("Boo!")])))
        result.add(new TestMethodResult("some skipped test", new DefaultTestResult(SKIPPED, startTime + 35, startTime + 45, 1, 0, 1, asList())))

        provider.provideOutputs("com.foo.FooTest", TestOutputEvent.Destination.StdOut, sw) >> {
            sw.write("1st output message\n")
            sw.write("2nd output message\n")
        }
        provider.provideOutputs("com.foo.FooTest", TestOutputEvent.Destination.StdErr, sw) >> { sw.write("err") }

        when:
        generator.write("com.foo.FooTest", result, sw)

        then:
        new JUnitTestClassExecutionResult(sw.toString(), "com.foo.FooTest")
            .assertTestCount(4, 1, 0)
            .assertTestFailed("some failing test", equalTo('java.lang.RuntimeException: Boo!'))
            .assertTestsSkipped("some skipped test")
            .assertTestsExecuted("some test", "some test two", "some failing test")
            .assertStdout(equalTo("""1st output message
2nd output message
"""))
            .assertStderr(equalTo("err"))

        and:
        sw.toString().startsWith """<?xml version=${startQuoteChar}1.0${startQuoteChar} encoding=${startQuoteChar}UTF-8${startQuoteChar}?>
  <testsuite name="com.foo.FooTest" tests="4" failures="1" errors="0" timestamp="2012-11-19T17:09:28" hostname="localhost" time="0.045">
  <properties/>
    <testcase name="some test" classname="com.foo.FooTest" time="0.015"></testcase>
    <testcase name="some test two" classname="com.foo.FooTest" time="0.015"></testcase>
    <testcase name="some failing test" classname="com.foo.FooTest" time="0.01">
      <failure message="java.lang.RuntimeException: Boo!" type="java.lang.RuntimeException">java.lang.RuntimeException: Boo!"""

        sw.toString().endsWith """</failure></testcase>
    <ignored-testcase name="some skipped test" classname="com.foo.FooTest" time="0.01"></ignored-testcase>
  <system-out><![CDATA[1st output message
2nd output message
]]></system-out>
  <system-err><![CDATA[err]]></system-err>
</testsuite>"""
    }

    def "writes results with empty outputs"() {
        StringWriter sw = new StringWriter()
        TestClassResult result = new TestClassResult(startTime)
        result.add(new TestMethodResult("some test", new DefaultTestResult(SUCCESS, startTime + 100, startTime + 300, 1, 1, 0, emptyList())))

        when:
        generator.write("com.foo.FooTest", result, sw)

        then:
        sw.toString() == """<?xml version=${startQuoteChar}1.0${startQuoteChar} encoding=${startQuoteChar}UTF-8${startQuoteChar}?>
  <testsuite name="com.foo.FooTest" tests="1" failures="0" errors="0" timestamp="2012-11-19T17:09:28" hostname="localhost" time="0.3">
  <properties/>
    <testcase name="some test" classname="com.foo.FooTest" time="0.2"></testcase>
  <system-out><![CDATA[]]></system-out>
  <system-err><![CDATA[]]></system-err>
</testsuite>"""
    }

    def "encodes xml"() {
        StringWriter sw = new StringWriter()
        TestClassResult result = new TestClassResult(startTime)
        result.add(new TestMethodResult("some test", new DefaultTestResult(FAILURE, 100, 300, 1, 1, 0, [new RuntimeException("<> encoded!")])))

        when:
        generator.write("com.foo.FooTest", result, sw)

        then:
        //attribute and text is encoded:
        sw.toString().contains('message="java.lang.RuntimeException: &lt;&gt; encoded!" type="java.lang.RuntimeException">java.lang.RuntimeException: &lt;&gt; encoded!')
    }

}
