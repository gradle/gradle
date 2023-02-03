/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

@TargetCoverage({ TestNGCoverage.SUPPORTED_BY_JDK })
class TestNGSuiteIntegrationTest extends MultiVersionIntegrationSpec {

    /**
     * https://discuss.gradle.org/t/npe-when-accessing-missing-property-in-usetestng-block-2-11-nightly/13226
     * https://discuss.gradle.org/t/calling-suitexmlbuilder-changes-invocation-context-in-usetestng-block-2-11-nightly/13227
     */
    def "can reference suiteXmlBuilder"() {
        given:
        buildFile << '''
            apply plugin: 'java'
            test {
              useTestNG {
                println "Property from task: $name"
                suiteXmlBuilder()
                println "Property from task again: $name"
              }
            }
        '''

        when:
        run "help"

        then:
        noExceptionThrown()

        and:
        outputContains 'Property from task: test'
        outputContains 'Property from task again: test'
    }

    def "methodMissing propagates failures"() {
        buildFile << """
    apply plugin: 'java'
    ${mavenCentralRepository()}
    dependencies {
        testImplementation 'org.testng:testng:$version'
    }
    test {
        useTestNG {
            systemProperty 'name', 'value'
        }
    }
"""
        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                @Test public void foo() {
                  assert System.getProperty("name").equals("value");
                }
            }
        """
        when:
        succeeds("test")
        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("FooTest").assertTestsExecuted("foo")
    }

    @Issue("GRADLE-3020")
    def "can specify test suite by string"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:$version'
            }
            test {
              useTestNG {
                suites "suite.xml"
              }
            }
        """

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                @org.testng.annotations.Test public void pass() {}
            }
        """
        file("src/test/java/BarTest.java") << """
            public class BarTest {
                @org.testng.annotations.Test public void pass() {}
            }
        """
        file("src/test/java/BazTest.java") << """
            public class BazTest {
                @org.testng.annotations.Test public void pass() {}
            }
        """

        file("suite.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="AwesomeSuite" parallel="tests">
  <test name="AwesomeTest" preserve-order="false">
    <classes>
      <class name="FooTest"/>
      <class name="BarTest"/>
    </classes>
  </test>
</suite>"""

        when: run("test")
        then: new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted('FooTest', 'BarTest')
    }
}
