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

import org.gradle.api.internal.tasks.testing.BuildableTestResultsProvider
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.integtests.fixtures.JUnitTestClassExecutionResult
import org.gradle.integtests.fixtures.TestResultOutputAssociation
import org.gradle.internal.SystemProperties
import spock.lang.Issue
import spock.lang.Specification

import static java.util.Collections.emptyList
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut
import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED
import static org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS
import static org.hamcrest.CoreMatchers.equalTo

class JUnitXmlResultWriterSpec extends Specification {
    private provider = Mock(TestResultsProvider)

    private startTime = 1353344968049

    def "writes xml JUnit result"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        TestClassResult result = new TestClassResult(1, "com.foo.FooTest", startTime)
        result.add(new TestMethodResult(1, "some test", SUCCESS, 15, startTime + 25))
        result.add(new TestMethodResult(2, "some test two", SUCCESS, 15, startTime + 30))
        result.add(new TestMethodResult(3, "some failing test", FAILURE, 10, startTime + 40).addFailure("failure message", "[stack-trace]", "ExceptionType"))
        result.add(new TestMethodResult(4, "some skipped test", SKIPPED, 10, startTime + 45))

        provider.writeAllOutput(1, StdOut, _) >> { args -> args[2].write("1st output message\n2nd output message\n") }
        provider.writeAllOutput(1, StdErr, _) >> { args -> args[2].write("err") }

        when:
        def xml = getXml(result, options)

        then:
        new JUnitTestClassExecutionResult(xml, "com.foo.FooTest", "com.foo.FooTest", TestResultOutputAssociation.WITH_SUITE)
            .assertTestCount(4, 1, 1, 0)
            .assertTestFailed("some failing test", equalTo('failure message'))
            .assertTestsSkipped("some skipped test")
            .assertTestsExecuted("some test", "some test two", "some failing test")
            .assertStdout(equalTo("""1st output message
2nd output message
"""))
            .assertStderr(equalTo("err"))

        and:
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="4" skipped="1" failures="1" errors="0" timestamp="2012-11-19T17:09:28.049Z" hostname="localhost" time="0.045">
  <properties/>
  <testcase name="some test" classname="com.foo.FooTest" time="0.015"/>
  <testcase name="some test two" classname="com.foo.FooTest" time="0.015"/>
  <testcase name="some failing test" classname="com.foo.FooTest" time="0.01">
    <failure message="failure message" type="ExceptionType">[stack-trace]</failure>
  </testcase>
  <testcase name="some skipped test" classname="com.foo.FooTest" time="0.01">
    <skipped/>
  </testcase>
  <system-out><![CDATA[1st output message
2nd output message
]]></system-out>
  <system-err><![CDATA[err]]></system-err>
</testsuite>
"""
    }

    def "writes results with empty outputs"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        TestClassResult result = new TestClassResult(1, "com.foo.FooTest", startTime)
        result.add(new TestMethodResult(1, "some test").completed(new DefaultTestResult(SUCCESS, startTime + 100, startTime + 300, 1, 1, 0, emptyList())))
        _ * provider.writeAllOutput(_, _, _)

        when:
        def xml = getXml(result, options)

        then:
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="2012-11-19T17:09:28.049Z" hostname="localhost" time="0.3">
  <properties/>
  <testcase name="some test" classname="com.foo.FooTest" time="0.2"/>
  <system-out><![CDATA[]]></system-out>
  <system-err><![CDATA[]]></system-err>
</testsuite>
"""
    }

    def "encodes xml"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        TestClassResult result = new TestClassResult(1, "com.foo.FooTest", startTime)
        result.add(new TestMethodResult(1, "some \ud8d3\ude01 test", FAILURE, 200, 300).addFailure("<> encoded!\ud8d3\ude02", "<non ascii:\ud8d3\ude02 \u0302>", "<Exception\ud8d3\ude29>"))
        provider.writeAllOutput(_, StdErr, _) >> { args -> args[2].write("with \ud8d3\ude31CDATA end token: ]]> some ascii: ż") }
        provider.writeAllOutput(_, StdOut, _) >> { args -> args[2].write("with CDATA end token: ]]> some ascii: \ud8d3\udd20ż") }

        when:
        def xml = getXml(result, options)

        then:
        //attribute and text is encoded:
        xml.contains('message="&lt;&gt; encoded!&#x44e02;" type="&lt;Exception&#x44e29;&gt;">&lt;non ascii:&#x44e02; \u0302&gt;')
        //output encoded:
        xml.contains('<system-out><![CDATA[with CDATA end token: ]]]]><![CDATA[> some ascii: ]]>&#x44d20;<![CDATA[ż]]></system-out>')
        xml.contains('<system-err><![CDATA[with ]]>&#x44e31;<![CDATA[CDATA end token: ]]]]><![CDATA[> some ascii: ż]]></system-err>')
    }

    def "writes results with no tests"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        TestClassResult result = new TestClassResult(1, "com.foo.IgnoredTest", startTime)

        when:
        def xml = getXml(result, options)

        then:
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.IgnoredTest" tests="0" skipped="0" failures="0" errors="0" timestamp="2012-11-19T17:09:28.049Z" hostname="localhost" time="0.0">
  <properties/>
  <system-out><![CDATA[]]></system-out>
  <system-err><![CDATA[]]></system-err>
</testsuite>
"""
    }

    def "can generate with output per test"() {
        given:
        def options = new JUnitXmlResultOptions(true, false, true, true)

        and:
        provider = new BuildableTestResultsProvider()

        when:
        def testClass = provider.testClassResult("com.Foo") {
            stdout "class-out"
            stderr "class-err"
            testcase("m1") {
                stderr " m1-err-1"
                stdout " m1-out-1"
                stdout " m1-out-2"
                stderr " m1-err-2"
            }
            testcase("m2") {
                stderr " m2-err-1"
                stdout " m2-out-1"
                stdout " m2-out-2"
                stderr " m2-err-2"
            }
        }

        then:
        getXml(testClass, options) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Foo" tests="2" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00Z" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="m1" classname="com.Foo" time="1.0">
    <system-out><![CDATA[ m1-out-1 m1-out-2]]></system-out>
    <system-err><![CDATA[ m1-err-1 m1-err-2]]></system-err>
  </testcase>
  <testcase name="m2" classname="com.Foo" time="1.0">
    <system-out><![CDATA[ m2-out-1 m2-out-2]]></system-out>
    <system-err><![CDATA[ m2-err-1 m2-err-2]]></system-err>
  </testcase>
  <system-out><![CDATA[class-out]]></system-out>
  <system-err><![CDATA[class-err]]></system-err>
</testsuite>
"""
    }

    def "can generate report with failed tests with no exception"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        TestClassResult result = new TestClassResult(1, "com.foo.FooTest", startTime)
        result.add(new TestMethodResult(3, "some failing test", FAILURE, 10, startTime + 40))

        when:
        def xml = getXml(result, options)

        then:
        new JUnitTestClassExecutionResult(xml, "com.foo.FooTest", "com.foo.FooTest", TestResultOutputAssociation.WITH_SUITE)
            .assertTestCount(1, 0, 1, 0)
            .assertTestFailed("some failing test")
    }

    @Issue("gradle/gradle#11445")
    def "writes #writtenName as class display name when #displayName is specified"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        TestClassResult result = new TestClassResult(1, "com.foo.FooTest", displayName, startTime)
        result.add(new TestMethodResult(1, "some test", "some test displayName", SUCCESS, 15, startTime + 25))
        result.add(new TestMethodResult(2, "some test two", "some test two displayName", SUCCESS, 15, startTime + 30))
        result.add(new TestMethodResult(3, "some failing test", "some failing test displayName", FAILURE, 10, startTime + 40).addFailure("failure message", "[stack-trace]", "ExceptionType"))
        result.add(new TestMethodResult(4, "some skipped test", "some skipped test displayName", SKIPPED, 10, startTime + 45))

        provider.writeAllOutput(1, StdOut, _) >> { args -> args[2].write("1st output message\n2nd output message\n") }
        provider.writeAllOutput(1, StdErr, _) >> { args -> args[2].write("err") }

        when:
        def xml = getXml(result, options)

        then:
        new JUnitTestClassExecutionResult(xml, "com.foo.FooTest", writtenName, TestResultOutputAssociation.WITH_SUITE)
            .assertTestCount(4, 1, 1, 0)
            .assertTestFailed("some failing test displayName", equalTo('failure message'))
            .assertTestsSkipped("some skipped test displayName")
            .assertTestsExecuted("some test displayName", "some test two displayName", "some failing test displayName")
            .assertStdout(equalTo("""1st output message
2nd output message
"""))
            .assertStderr(equalTo("err"))

        and:
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="$writtenName" tests="4" skipped="1" failures="1" errors="0" timestamp="2012-11-19T17:09:28.049Z" hostname="localhost" time="0.045">
  <properties/>
  <testcase name="some test displayName" classname="com.foo.FooTest" time="0.015"/>
  <testcase name="some test two displayName" classname="com.foo.FooTest" time="0.015"/>
  <testcase name="some failing test displayName" classname="com.foo.FooTest" time="0.01">
    <failure message="failure message" type="ExceptionType">[stack-trace]</failure>
  </testcase>
  <testcase name="some skipped test displayName" classname="com.foo.FooTest" time="0.01">
    <skipped/>
  </testcase>
  <system-out><![CDATA[1st output message
2nd output message
]]></system-out>
  <system-err><![CDATA[err]]></system-err>
</testsuite>
"""

        where:
        displayName           | writtenName
        'FooTest'             | 'com.foo.FooTest'
        'custom display name' | 'custom display name'
    }

    @Issue("https://github.com/gradle/gradle/issues/23229")
    def "omit system-out section"() {
        given:
        def options = new JUnitXmlResultOptions(true, false, false, true)
        provider = new BuildableTestResultsProvider()

        when:
        def testClass = generateTestClassWithOutput(provider)

        then:
        def xml = getXml(testClass, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00Z" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="test-case" classname="com.foo.FooTest" time="1.0">
    <system-err><![CDATA[test-err]]></system-err>
  </testcase>
  <system-err><![CDATA[suite-err]]></system-err>
</testsuite>
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/23229")
    def "omit system-err section"() {
        given:
        def options = new JUnitXmlResultOptions(true, false, true, false)
        provider = new BuildableTestResultsProvider()

        when:
        def testClass = generateTestClassWithOutput(provider)

        then:
        def xml = getXml(testClass, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00Z" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="test-case" classname="com.foo.FooTest" time="1.0">
    <system-out><![CDATA[test-out]]></system-out>
  </testcase>
  <system-out><![CDATA[suite-out]]></system-out>
</testsuite>
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/23229")
    def "show system-err and system-out sections"() {
        given:
        def options = new JUnitXmlResultOptions(true, false, true, true)
        provider = new BuildableTestResultsProvider()

        when:
        def testClass = generateTestClassWithOutput(provider)

        then:
        def xml = getXml(testClass, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00Z" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="test-case" classname="com.foo.FooTest" time="1.0">
    <system-out><![CDATA[test-out]]></system-out>
    <system-err><![CDATA[test-err]]></system-err>
  </testcase>
  <system-out><![CDATA[suite-out]]></system-out>
  <system-err><![CDATA[suite-err]]></system-err>
</testsuite>
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/23229")
    def "omit system-err and system-out sections"() {
        given:
        def options = new JUnitXmlResultOptions(true, false, false, false)
        provider = new BuildableTestResultsProvider()

        when:
        def testClass = generateTestClassWithOutput(provider)

        then:
        def xml = getXml(testClass, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00Z" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="test-case" classname="com.foo.FooTest" time="1.0"/>
</testsuite>
"""
    }

    private String getXml(TestClassResult result, JUnitXmlResultOptions options) {
        def text = new ByteArrayOutputStream()
        getGenerator(options).write(result, text)
        return text.toString("UTF-8").replace(SystemProperties.instance.lineSeparator, "\n")
    }

    private JUnitXmlResultWriter getGenerator(JUnitXmlResultOptions options) {
        return new JUnitXmlResultWriter("localhost", provider, options)
    }

    private BuildableTestResultsProvider.BuildableTestClassResult generateTestClassWithOutput(BuildableTestResultsProvider provider) {
        provider.testClassResult("com.foo.FooTest") {
            stdout "suite-out"
            stderr "suite-err"
            testcase("test-case") {
                stderr "test-err"
                stdout "test-out"
            }
        }
    }
}
