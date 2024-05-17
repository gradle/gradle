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
package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.integtests.fixtures.TestNGExecutionResult

class TestNGProducesOldReportsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.noExtraLogging()
    }

    def "always produces the new xml reports"() {
        given:
        file("src/test/java/org/MixedMethodsTest.java") << """package org;
import org.testng.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class MixedMethodsTest {
    @Test public void passing() {
    }
    @Test public void failing() {
        fail("failing!");
    }
}
"""
        def buildFile = file('build.gradle')
        buildFile << """
apply plugin: 'java'
${mavenCentralRepository()}
dependencies { testImplementation 'org.testng:testng:6.3.1' }

test {
    reports.html.required = false
    useTestNG()
}
"""
        when:
        executer.withTasks('test').runWithFailure().assertTestsFailed()

        then:
        !new TestNGExecutionResult(file(".")).hasTestNGXmlResults()
        new JUnitXmlTestExecutionResult(file(".")).hasJUnitXmlResults()
    }

    def "can generate the old xml reports"() {
        given:
        file("src/test/java/org/SomeTest.java") << """package org;
import org.testng.annotations.*;

public class SomeTest {
    @Test public void passing() {}
}
"""
        def buildFile = file('build.gradle')
        buildFile << """
apply plugin: 'java'
${mavenCentralRepository()}
dependencies { testImplementation 'org.testng:testng:6.3.1' }
test {
  reports.html.required = false
  $preConfig
  useTestNG(){
    useDefaultListeners = true
  }
  $postConfig
}
"""
        when:
        executer.withTasks('test').run()

        then:
        new JUnitXmlTestExecutionResult(file(".")).hasJUnitXmlResults()

        def testNG = new TestNGExecutionResult(file("."), path)
        testNG.hasTestNGXmlResults()
        testNG.hasJUnitResultsGeneratedByTestNG()
        testNG.hasHtmlResults()

        where:
        preConfig                                   | postConfig                                                                              | path
        ""                                          | ""                                                                                      | TestNGExecutionResult.DEFAULT_TESTNG_REPORT
        "reports.html.outputLocation = file('xyz')" | "reports.html.outputLocation = file('abc')"                                             | "abc"
        ""                                          | "reports.html.outputLocation = file('abc')"                                             | "abc"
        "reports.html.outputLocation = file('abc')" | "options.outputDirectory = file('xyz')"                                                 | "xyz"
        ""                                          | "options.outputDirectory = file('xyz');reports.html.outputLocation = file('ignore me')" | "xyz"
    }
}
