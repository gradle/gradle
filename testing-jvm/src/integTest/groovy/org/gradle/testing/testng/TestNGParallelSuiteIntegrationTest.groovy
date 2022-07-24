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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

@TargetCoverage({ TestNGCoverage.SUPPORTED_BY_JDK })
class TestNGParallelSuiteIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
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
}
