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

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

@TargetCoverage({ TestNGCoverage.STANDARD_COVERAGE })
class TestNGParallelSuiteIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile 'org.testng:testng:$version'
            }
            test {
              useTestNG {
                suites "suite.xml"
              }
            }
        """
    }

    def createTests(int testCount, int threadCount) {
        String suiteXml = ""
        testCount.times { x ->
            file("src/test/java/Foo${x}Test.java") << """
                public class Foo${x}Test {
                    @org.testng.annotations.Test public void test() {
                        for (int i=0; i<20; i++) {
                            System.out.println("" + i + " - foo ${x} - " + Thread.currentThread().getId());
                        }
                    }
                }
            """
            suiteXml += "<test name='t${x}'><classes><class name='Foo${x}Test'/></classes></test>\n"
        }

        file("suite.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="AwesomeSuite" parallel="tests" thread-count="${threadCount}">
  $suiteXml
</suite>"""
    }

    @Issue("GRADLE-3190")
    def "runs with multiple parallel threads"() {
        given:
        createTests(200, 20)

        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.testClass("Foo0Test").assertTestsExecuted("test")
        result.testClass("Foo199Test").assertTestsExecuted("test")
    }

    @Issue("https://github.com/gradle/gradle/issues/4457")
    def "can persist configurations in xml"() {
        given:
        createTests(3, 3)

        when:
        run('test', '--info')

        then:
        actualThreadIds(output).size() == 3
    }

    private static actualThreadIds(String stdout) {
        String pattern = /.*\d+ - foo \d+ - (\d+)/
        return stdout.readLines().grep(~pattern).collect { (it =~ pattern)[0][1] }.toSet()
    }

    @Issue("https://github.com/gradle/gradle/issues/2783")
    def "executes TestNG-based tests defined in a suite only once when run with multiple forks"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile 'org.testng:testng:$version'
            }
            test {
              maxParallelForks = 2
                test {
              testLogging {
                events 'started'
              }
              }
              useTestNG {
                suites "testng.xml"
              }
            }
        """

        file('testng.xml') << """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
            <suite name="unit tests" verbose="1" time-out="0">
               <test name="simple">
                  <classes>
                     <class name="MyTest1" />
                  </classes>
               </test>
            </suite>
        """

        5.times {  file("src/test/java/MyTest${it}.java") <<
            """                
            public class MyTest${it} {
                @org.testng.annotations.Test
                public void aTest() {}
                
                @org.testng.annotations.Test
                public void bTest() {}
            }
            """
        }

        when:
        succeeds('test')

        then:
        assertTestInvocations('unit tests > simple > MyTest1.aTest STARTED', 1)
        assertTestInvocations('unit tests > simple > MyTest1.bTest STARTED', 1)
        assertTestInvocations('unit tests > simple > MyTest0.aTest STARTED', 0)
        assertTestInvocations('unit tests > simple > MyTest4.bTest STARTED', 0)
    }

    @Issue("https://github.com/gradle/gradle/issues/2783")
    def "executes TestNG-based tests defined in multiple suite only once when run with multiple forks"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testCompile 'org.testng:testng:$version'
            }
            test {
              maxParallelForks = 2
                test {
              testLogging {
                events 'started'
              }
              }
              useTestNG {
                suites "testng-0.xml"
                suites "testng-1.xml"
              }
            }
        """

        5.times {
            file("testng-${it}.xml") << """<?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
                <suite name="unit tests" verbose="1" time-out="0">
                   <test name="simple-${it}">
                      <classes>
                         <class name="MyTest${it}" />
                      </classes>
                   </test>
                </suite>
            """

            file("src/test/java/MyTest${it}.java") << """                
                public class MyTest${it} {
                    @org.testng.annotations.Test
                    public void aTest() {}
                    
                    @org.testng.annotations.Test
                    public void bTest() {}
                }
            """
        }


        when:
        succeeds('test')

        then:
        assertTestInvocations('unit tests > simple-0 > MyTest0.aTest STARTED', 1)
        assertTestInvocations('unit tests > simple-0 > MyTest0.bTest STARTED', 1)
        assertTestInvocations('unit tests > simple-1 > MyTest1.aTest STARTED', 1)
        assertTestInvocations('unit tests > simple-1 > MyTest1.bTest STARTED', 1)

        assertTestInvocations('unit tests > simple-1 > MyTest0.aTest STARTED', 0)
        assertTestInvocations('unit tests > simple-1 > MyTest0.bTest STARTED', 0)
        assertTestInvocations('unit tests > simple-0 > MyTest1.aTest STARTED', 0)
        assertTestInvocations('unit tests > simple-0 > MyTest1.bTest STARTED', 0)
    }

    private void assertTestInvocations(String expectedOutput, int count) {
        assert StringUtils.countMatches(output, expectedOutput) == count
    }
}
