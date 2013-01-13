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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TestReportIntegrationTest extends AbstractIntegrationSpec {
    // TODO - use default value for bin results dir, and deprecate
    // TODO - auto-wiring for test results
    // TODO - warn when duplicate class results are discarded
    // TODO - sample and int test
    def "can generate report from multiple test result dirs"() {
        given:
        writeTest("Test1")
        writeTest("Test2")
        writeTest("Test3")

        and:
        buildFile << """
apply plugin: 'java'

repositories { mavenCentral() }
dependencies { testCompile 'junit:junit:4.11' }

test { include 'Test1.class' }
task test2(type: Test) { include 'Test2.class' }
task test3(type: Test) { include 'Test3.class' }

task report(type: TestReport) {
    destinationDir = file("\${reporting.baseDir}/all-tests")
    dependsOn test, test2, test3
    testResultDirs = [test, test2, test3].collect { it.binResultsDir }
}
"""

        when:
        run "report"

        then:
        file("build/reports/all-tests/index.html").assertIsFile()
        file("build/reports/all-tests/Test1.html").text.contains("Hi from Test1")
        file("build/reports/all-tests/Test2.html").text.contains("Hi from Test2")
        file("build/reports/all-tests/Test3.html").text.contains("Hi from Test3")
    }

    private void writeTest(String testName) {
        file("src/test/java/${testName}.java") << """
public class ${testName} {
    @org.junit.Test
    public void test() {
        System.out.println("Hi from ${testName}");
    }
}
"""
    }
}
