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
import org.gradle.internal.SystemProperties
import spock.lang.Specification

class JUnitXmlResultWriterMergeRerunSpec extends Specification {

    def provider = new BuildableTestResultsProvider()
    def outputPerTestCase = true

    protected JUnitXmlResultWriter getGenerator() {
        new JUnitXmlResultWriter("localhost", provider, new JUnitXmlResultOptions(outputPerTestCase, true))
    }

    def "merges for simple case - output per testcase"() {
        when:
        def testClass = provider.testClassResult("com.Flaky") {
            stdout "class-out"
            stderr "class-err"
            testcase("m1") {
                stderr "m1-err-1"
                stdout "m1-out-1"
                failure "m1-message-1", "m1-stackTrace-1"
            }
            testcase("m1") {
                stdout "m1-out-2"
                stderr "m1-err-2"
            }
            testcase("m2") {
                stderr "m2-err-1"
                stdout "m2-out-1"
                failure "m2-message-1", "m2-stackTrace-1"
            }
            testcase("m2") {
                stderr "m2-err-2"
                stdout "m2-out-2"
                failure "m2-message-2", "m2-stackTrace-2"
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="4" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="m1" classname="com.Flaky" time="2.0">
    <flakyFailure message="m1-message-1" type="ExceptionType">
      <stackTrace>m1-stackTrace-1</stackTrace>
      <system-out><![CDATA[m1-out-1]]></system-out>
      <system-err><![CDATA[m1-err-1]]></system-err>
    </flakyFailure>
    <system-out><![CDATA[m1-out-2]]></system-out>
    <system-err><![CDATA[m1-err-2]]></system-err>
  </testcase>
  <testcase name="m2" classname="com.Flaky" time="1.0">
    <failure message="m2-message-1" type="ExceptionType">m2-stackTrace-1</failure>
    <system-out><![CDATA[m2-out-1]]></system-out>
    <system-err><![CDATA[m2-err-1]]></system-err>
    <rerunFailure message="m2-message-2" type="ExceptionType">
      <stackTrace>m2-stackTrace-2</stackTrace>
      <system-out><![CDATA[m2-out-2]]></system-out>
      <system-err><![CDATA[m2-err-2]]></system-err>
    </rerunFailure>
  </testcase>
  <system-out><![CDATA[class-out]]></system-out>
  <system-err><![CDATA[class-err]]></system-err>
</testsuite>
"""
    }

    def "merges for simple case - output at suite"() {
        when:
        outputPerTestCase = false
        def testClass = provider.testClassResult("com.Flaky") {
            stdout "class-out"
            stderr "class-err"
            testcase("m1") {
                stderr "m1-err-1"
                stdout "m1-out-1"
                failure "m1-message-1", "m1-stackTrace-1"
            }
            testcase("m1") {
                stdout "m1-out-2"
                stderr "m1-err-2"
            }
            testcase("m2") {
                stderr "m2-err-1"
                stdout "m2-out-1"
                failure "m2-message-1", "m2-stackTrace-1"
            }
            testcase("m2") {
                stderr "m2-err-2"
                stdout "m2-out-2"
                failure "m2-message-2", "m2-stackTrace-2"
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="4" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="m1" classname="com.Flaky" time="2.0">
    <flakyFailure message="m1-message-1" type="ExceptionType">
      <stackTrace>m1-stackTrace-1</stackTrace>
    </flakyFailure>
  </testcase>
  <testcase name="m2" classname="com.Flaky" time="1.0">
    <failure message="m2-message-1" type="ExceptionType">m2-stackTrace-1</failure>
    <rerunFailure message="m2-message-2" type="ExceptionType">
      <stackTrace>m2-stackTrace-2</stackTrace>
    </rerunFailure>
  </testcase>
  <system-out><![CDATA[class-outm1-out-1m1-out-2m2-out-1m2-out-2]]></system-out>
  <system-err><![CDATA[class-errm1-err-1m1-err-2m2-err-1m2-err-2]]></system-err>
</testsuite>
"""
    }

    def "can have many failing executions"() {
        when:
        def testClass = provider.testClassResult("com.Flaky") {
            stdout "class-out"
            stderr "class-err"
            testcase("m1") {
                stderr "m1-err-1"
                stdout "m1-out-1"
                failure "m1-message-1", "m1-stackTrace-1"
            }
            testcase("m2") {
                stderr "m2-err-1"
                stdout "m2-out-1"
                failure "m2-message-1", "m2-stackTrace-1"
            }
            testcase("m1") {
                stderr "m1-err-2"
                stdout "m1-out-2"
                failure "m1-message-2", "m1-stackTrace-2"
            }
            testcase("m2") {
                stderr "m2-err-2"
                stdout "m2-out-2"
                failure "m2-message-2", "m2-stackTrace-2"
            }
            testcase("m1") {
                stdout "m1-out-3"
                stderr "m1-err-3"
            }
            testcase("m2") {
                stderr "m2-err-3"
                stdout "m2-out-3"
                failure "m2-message-3", "m2-stackTrace-3"
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="6" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="m1" classname="com.Flaky" time="3.0">
    <flakyFailure message="m1-message-1" type="ExceptionType">
      <stackTrace>m1-stackTrace-1</stackTrace>
      <system-out><![CDATA[m1-out-1]]></system-out>
      <system-err><![CDATA[m1-err-1]]></system-err>
    </flakyFailure>
    <flakyFailure message="m1-message-2" type="ExceptionType">
      <stackTrace>m1-stackTrace-2</stackTrace>
      <system-out><![CDATA[m1-out-2]]></system-out>
      <system-err><![CDATA[m1-err-2]]></system-err>
    </flakyFailure>
    <system-out><![CDATA[m1-out-3]]></system-out>
    <system-err><![CDATA[m1-err-3]]></system-err>
  </testcase>
  <testcase name="m2" classname="com.Flaky" time="1.0">
    <failure message="m2-message-1" type="ExceptionType">m2-stackTrace-1</failure>
    <system-out><![CDATA[m2-out-1]]></system-out>
    <system-err><![CDATA[m2-err-1]]></system-err>
    <rerunFailure message="m2-message-2" type="ExceptionType">
      <stackTrace>m2-stackTrace-2</stackTrace>
      <system-out><![CDATA[m2-out-2]]></system-out>
      <system-err><![CDATA[m2-err-2]]></system-err>
    </rerunFailure>
    <rerunFailure message="m2-message-3" type="ExceptionType">
      <stackTrace>m2-stackTrace-3</stackTrace>
      <system-out><![CDATA[m2-out-3]]></system-out>
      <system-err><![CDATA[m2-err-3]]></system-err>
    </rerunFailure>
  </testcase>
  <system-out><![CDATA[class-out]]></system-out>
  <system-err><![CDATA[class-err]]></system-err>
</testsuite>
"""
    }

    def "breaks up into testcases terminated by non failed results"() {
        when:
        def testClass = provider.testClassResult("com.Flaky") {
            testcase("m1") {
                failure "m1-message-1", "m1-stackTrace-1"
            }
            testcase("m1") {
                failure "m1-message-2", "m1-stackTrace-2"
            }
            testcase("m1") {

            }
            testcase("m1") {
                failure "m1-message-3", "m1-stackTrace-3"
            }
            testcase("m1") {
                ignore()
            }
            testcase("m1") {
                failure "m1-message-4", "m1-stackTrace-4"
            }
            testcase("m1") {
                failure "m1-message-5", "m1-stackTrace-5"
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="7" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="m1" classname="com.Flaky" time="3.0">
    <flakyFailure message="m1-message-1" type="ExceptionType">
      <stackTrace>m1-stackTrace-1</stackTrace>
    </flakyFailure>
    <flakyFailure message="m1-message-2" type="ExceptionType">
      <stackTrace>m1-stackTrace-2</stackTrace>
    </flakyFailure>
  </testcase>
  <testcase name="m1" classname="com.Flaky" time="5.0">
    <flakyFailure message="m1-message-3" type="ExceptionType">
      <stackTrace>m1-stackTrace-3</stackTrace>
    </flakyFailure>
    <skipped/>
  </testcase>
  <testcase name="m1" classname="com.Flaky" time="6.0">
    <failure message="m1-message-4" type="ExceptionType">m1-stackTrace-4</failure>
    <rerunFailure message="m1-message-5" type="ExceptionType">
      <stackTrace>m1-stackTrace-5</stackTrace>
    </rerunFailure>
  </testcase>
  <system-out><![CDATA[]]></system-out>
  <system-err><![CDATA[]]></system-err>
</testsuite>
"""
    }

    def "unpacks multiple failures"() {
        when:
        def testClass = provider.testClassResult("com.Flaky") {
            stdout "class-out"
            stderr "class-err"
            testcase("m1") {
                stderr "m1-err-1"
                stdout "m1-out-1"
                failure "m1-message-1.1", "m1-stackTrace-1.1"
                failure "m1-message-1.2", "m1-stackTrace-1.2"
            }
            testcase("m1") {
                stdout "m1-out-2"
                stderr "m1-err-2"
            }
            testcase("m2") {
                stderr "m2-err-1"
                stdout "m2-out-1"
                failure "m2-message-1.1", "m2-stackTrace-1.1"
                failure "m2-message-1.2", "m2-stackTrace-1.2"
            }
            testcase("m2") {
                stderr "m2-err-2"
                stdout "m2-out-2"
                failure "m2-message-2.1", "m2-stackTrace-2.1"
                failure "m2-message-2.2", "m2-stackTrace-2.2"
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="4" skipped="0" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
  <properties/>
  <testcase name="m1" classname="com.Flaky" time="2.0">
    <flakyFailure message="m1-message-1.1" type="ExceptionType">
      <stackTrace>m1-stackTrace-1.1</stackTrace>
      <system-out><![CDATA[m1-out-1]]></system-out>
      <system-err><![CDATA[m1-err-1]]></system-err>
    </flakyFailure>
    <flakyFailure message="m1-message-1.2" type="ExceptionType">
      <stackTrace>m1-stackTrace-1.2</stackTrace>
    </flakyFailure>
    <system-out><![CDATA[m1-out-2]]></system-out>
    <system-err><![CDATA[m1-err-2]]></system-err>
  </testcase>
  <testcase name="m2" classname="com.Flaky" time="1.0">
    <failure message="m2-message-1.1" type="ExceptionType">m2-stackTrace-1.1</failure>
    <system-out><![CDATA[m2-out-1]]></system-out>
    <system-err><![CDATA[m2-err-1]]></system-err>
    <failure message="m2-message-1.2" type="ExceptionType">m2-stackTrace-1.2</failure>
    <rerunFailure message="m2-message-2.1" type="ExceptionType">
      <stackTrace>m2-stackTrace-2.1</stackTrace>
      <system-out><![CDATA[m2-out-2]]></system-out>
      <system-err><![CDATA[m2-err-2]]></system-err>
    </rerunFailure>
    <rerunFailure message="m2-message-2.2" type="ExceptionType">
      <stackTrace>m2-stackTrace-2.2</stackTrace>
    </rerunFailure>
  </testcase>
  <system-out><![CDATA[class-out]]></system-out>
  <system-err><![CDATA[class-err]]></system-err>
</testsuite>
"""
    }

    def getXml(TestClassResult result) {
        def text = new ByteArrayOutputStream()
        generator.write(result, text)
        return text.toString("UTF-8").replace(SystemProperties.instance.lineSeparator, "\n")
    }
}
