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
import org.gradle.integtests.fixtures.JUnitTestClassExecutionResult
import org.gradle.integtests.fixtures.TestResultOutputAssociation
import org.gradle.internal.SystemProperties
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED
import static org.hamcrest.CoreMatchers.equalTo

class JUnitXmlResultWriterSpec extends Specification {
    private startTime = 1353344968049

    def "writes xml JUnit result"() {
        given:
        def options = new JUnitXmlResultOptions(false, false, true, true)

        and:
        def provider = new BuildableTestResultsProvider()
        provider.with {
            result("com.foo.FooTest") {
                startTime(this.startTime)
                endTime(this.startTime + 45)
            }
            stdout("1st output message\n2nd output message\n")
            stderr("err")
            child {
                result("some test") {
                    startTime(this.startTime + 10)
                    endTime(this.startTime + 25)
                }
            }
            child {
                result("some test two") {
                    startTime(this.startTime + 15)
                    endTime(this.startTime + 30)
                }
            }
            child {
                result("some failing test") {
                    startTime(this.startTime + 30)
                    endTime(this.startTime + 40)
                    resultType(FAILURE)
                    addFailure(new PersistentTestFailure("failure message", "[stack-trace]", "ExceptionType"))
                }
            }
            child {
                result("some skipped test") {
                    startTime(this.startTime + 35)
                    endTime(this.startTime + 45)
                    resultType(SKIPPED)
                }
            }
        }

        when:
        def xml = getXml(provider, options)

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
<testsuite name="com.foo.FooTest" tests="4" skipped="1" failures="1" errors="0" timestamp="2012-11-19T17:09:28" hostname="localhost" time="0.045">
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
        def provider = new BuildableTestResultsProvider()
        provider.with {
            result("com.foo.FooTest") {
                startTime(this.startTime)
                endTime(this.startTime + 300)
            }
            child {
                result("some test") {
                    startTime(this.startTime + 100)
                    endTime(this.startTime + 300)
                }
            }
        }

        when:
        def xml = getXml(provider, options)

        then:
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="2012-11-19T17:09:28" hostname="localhost" time="0.3">
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
        def provider = new BuildableTestResultsProvider()
        provider.with {
            result("com.foo.FooTest") {
                startTime(this.startTime)
                endTime(this.startTime + 300)
            }
            stdout("with CDATA end token: ]]> some ascii: \ud8d3\udd20ż")
            stderr("with \ud8d3\ude31CDATA end token: ]]> some ascii: ż")
            child {
                result("some \ud8d3\ude01 test") {
                    startTime(this.startTime + 100)
                    endTime(this.startTime + 300)
                    resultType(FAILURE)
                    addFailure(new PersistentTestFailure("<> encoded!\ud8d3\ude02", "<non ascii:\ud8d3\ude02 \u0302>", "<Exception\ud8d3\ude29>"))
                }
            }
        }

        when:
        def xml = getXml(provider, options)

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
        def provider = new BuildableTestResultsProvider()
        provider.with {
            result("com.foo.IgnoredTest") {
                startTime(this.startTime)
                endTime(this.startTime)
            }
        }

        when:
        def xml = getXml(provider, options)

        then:
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.IgnoredTest" tests="0" skipped="0" failures="0" errors="0" timestamp="2012-11-19T17:09:28" hostname="localhost" time="0.0">
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
        def provider = new BuildableTestResultsProvider()

        when:
        provider.with {
            result("com.Foo") {
                startTime(0)
                endTime(1000)
            }
            stdout "class-out"
            stderr "class-err"
            child {
                result("m1") {
                    startTime(0)
                    endTime(1000)
                }
                stderr " m1-err-1"
                stdout " m1-out-1"
                stdout " m1-out-2"
                stderr " m1-err-2"
            }
            child {
                result("m2") {
                    startTime(0)
                    endTime(1000)
                }
                stderr " m2-err-1"
                stdout " m2-out-1"
                stdout " m2-out-2"
                stderr " m2-err-2"
            }
        }

        then:
        getXml(provider, options) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Foo" tests="2" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def provider = new BuildableTestResultsProvider()
        provider.with {
            result("com.foo.FooTest") {
                startTime(this.startTime)
            }
            child {
                result("some failing test") {
                    startTime(this.startTime + 30)
                    endTime(this.startTime + 40)
                    resultType(FAILURE)
                }
            }
        }

        when:
        def xml = getXml(provider, options)

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
        def provider = new BuildableTestResultsProvider()
        provider.with {
            result("com.foo.FooTest") {
                delegate.displayName(displayName)
                startTime(this.startTime)
                endTime(this.startTime + 45)
            }
            stdout("1st output message\n2nd output message\n")
            stderr("err")
            child {
                result("some test") {
                    delegate.displayName("some test displayName")
                    startTime(this.startTime + 10)
                    endTime(this.startTime + 25)
                }
            }
            child {
                result("some test two") {
                    delegate.displayName("some test two displayName")
                    startTime(this.startTime + 15)
                    endTime(this.startTime + 30)
                }
            }
            child {
                result("some failing test") {
                    delegate.displayName("some failing test displayName")
                    startTime(this.startTime + 30)
                    endTime(this.startTime + 40)
                    resultType(FAILURE)
                    addFailure(new PersistentTestFailure("failure message", "[stack-trace]", "ExceptionType"))
                }
            }
            child {
                result("some skipped test") {
                    delegate.displayName("some skipped test displayName")
                    startTime(this.startTime + 35)
                    endTime(this.startTime + 45)
                    resultType(SKIPPED)
                }
            }
        }

        when:
        def xml = getXml(provider, options)

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
<testsuite name="$writtenName" tests="4" skipped="1" failures="1" errors="0" timestamp="2012-11-19T17:09:28" hostname="localhost" time="0.045">
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
        def provider = new BuildableTestResultsProvider()

        when:
        generateTestClassWithOutput(provider)

        then:
        def xml = getXml(provider, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def provider = new BuildableTestResultsProvider()

        when:
        generateTestClassWithOutput(provider)

        then:
        def xml = getXml(provider, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def provider = new BuildableTestResultsProvider()

        when:
        generateTestClassWithOutput(provider)

        then:
        def xml = getXml(provider, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def provider = new BuildableTestResultsProvider()

        when:
        generateTestClassWithOutput(provider)

        then:
        def xml = getXml(provider, options)
        xml == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.foo.FooTest" tests="1" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="test-case" classname="com.foo.FooTest" time="1.0"/>
</testsuite>
"""
    }

    private static String getXml(BuildableTestResultsProvider result, JUnitXmlResultOptions options) {
        def text = new ByteArrayOutputStream()
        getGenerator(options).write(result, text)
        return text.toString("UTF-8").replace(SystemProperties.instance.lineSeparator, "\n")
    }

    private static JUnitXmlResultWriter getGenerator(JUnitXmlResultOptions options) {
        return new JUnitXmlResultWriter("localhost", options)
    }

    private static void generateTestClassWithOutput(BuildableTestResultsProvider provider) {
        provider.with {
            result("com.foo.FooTest") {
                startTime(0)
                endTime(1000)
            }
            stdout("suite-out")
            stderr("suite-err")
            child {
                result("test-case") {
                    startTime(0)
                    endTime(1000)
                }
                stdout("test-out")
                stderr("test-err")
            }
        }
    }
}
