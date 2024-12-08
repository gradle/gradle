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
import org.gradle.api.internal.tasks.testing.report.AllTestResults
import org.gradle.api.internal.tasks.testing.report.ClassTestResults
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.SystemProperties
import spock.lang.Specification

class JUnitXmlResultWriterMergeRerunSpec extends Specification {

    def outputPerTestCase = true

    protected JUnitXmlResultWriter getGenerator() {
        new JUnitXmlResultWriter("localhost", new JUnitXmlResultOptions(outputPerTestCase, true, true, true))
    }

    def "merges for simple case - output per testcase"() {
        when:
        def testClass = new BuildableTestResultsProvider().tap {
            child {
                resultForClass("com.Flaky")
                stdout "class-out"
                stderr "class-err"
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-1", "m1-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m1-out-1"
                    stderr "m1-err-1"
                }
                child {
                    result("m1") {
                        endTime(2000)
                    }
                    stdout "m1-out-2"
                    stderr "m1-err-2"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-1", "m2-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-1"
                    stderr "m2-err-1"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-2", "m2-stackTrace-2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-2"
                    stderr "m2-err-2"
                }
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="4" skipped="0" failures="3" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def testClass = new BuildableTestResultsProvider().tap {
            child {
                resultForClass("com.Flaky")
                stdout "class-out"
                stderr "class-err"
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-1", "m1-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m1-out-1"
                    stderr "m1-err-1"
                }
                child {
                    result("m1") {
                        endTime(2000)
                    }
                    stdout "m1-out-2"
                    stderr "m1-err-2"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-1", "m2-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-1"
                    stderr "m2-err-1"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-2", "m2-stackTrace-2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-2"
                    stderr "m2-err-2"
                }
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="4" skipped="0" failures="3" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def testClass = new BuildableTestResultsProvider().tap {
            child {
                resultForClass("com.Flaky")
                stdout "class-out"
                stderr "class-err"
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-1", "m1-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m1-out-1"
                    stderr "m1-err-1"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-1", "m2-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-1"
                    stderr "m2-err-1"
                }
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-2", "m1-stackTrace-2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m1-out-2"
                    stderr "m1-err-2"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-2", "m2-stackTrace-2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-2"
                    stderr "m2-err-2"
                }
                child {
                    result("m1") {
                        endTime(3000)
                    }
                    stdout "m1-out-3"
                    stderr "m1-err-3"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-3", "m2-stackTrace-3", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-3"
                    stderr "m2-err-3"
                }
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="6" skipped="0" failures="5" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def testClass = new BuildableTestResultsProvider().tap {
            child {
                resultForClass("com.Flaky")
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-1", "m1-stackTrace-1", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                }
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-2", "m1-stackTrace-2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                }
                child {
                    result("m1") {
                        endTime(3000)
                    }
                }
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-3", "m1-stackTrace-3", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                }
                child {
                    result("m1") {
                        endTime(5000)
                        resultType(TestResult.ResultType.SKIPPED)
                    }
                }
                child {
                    result("m1") {
                        endTime(6000)
                        addFailure(new PersistentTestFailure("m1-message-4", "m1-stackTrace-4", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                }
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-5", "m1-stackTrace-5", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                }
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="7" skipped="1" failures="5" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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
        def testClass = new BuildableTestResultsProvider().tap {
            child {
                resultForClass("com.Flaky")
                stdout "class-out"
                stderr "class-err"
                child {
                    result("m1") {
                        addFailure(new PersistentTestFailure("m1-message-1.1", "m1-stackTrace-1.1", "ExceptionType"))
                        addFailure(new PersistentTestFailure("m1-message-1.2", "m1-stackTrace-1.2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m1-out-1"
                    stderr "m1-err-1"
                }
                child {
                    result("m1") {
                        endTime(2000)
                    }
                    stdout "m1-out-2"
                    stderr "m1-err-2"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-1.1", "m2-stackTrace-1.1", "ExceptionType"))
                        addFailure(new PersistentTestFailure("m2-message-1.2", "m2-stackTrace-1.2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-1"
                    stderr "m2-err-1"
                }
                child {
                    result("m2") {
                        addFailure(new PersistentTestFailure("m2-message-2.1", "m2-stackTrace-2.1", "ExceptionType"))
                        addFailure(new PersistentTestFailure("m2-message-2.2", "m2-stackTrace-2.2", "ExceptionType"))
                        resultType(TestResult.ResultType.FAILURE)
                    }
                    stdout "m2-out-2"
                    stderr "m2-err-2"
                }
            }
        }

        then:
        getXml(testClass) == """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.Flaky" tests="4" skipped="0" failures="3" errors="0" timestamp="1970-01-01T00:00:00" hostname="localhost" time="1.0">
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

    def getXml(TestResultsProvider result) {
        def text = new ByteArrayOutputStream()
        AllTestResults allResults = AllTestResults.loadModelFromProvider(result)
        assert allResults.packages.size() == 1
        assert allResults.packages[0].classes.size() == 1
        ClassTestResults classResults = allResults.packages[0].classes[0]
        generator.write(classResults, text)
        return text.toString("UTF-8").replace(SystemProperties.instance.lineSeparator, "\n")
    }
}
