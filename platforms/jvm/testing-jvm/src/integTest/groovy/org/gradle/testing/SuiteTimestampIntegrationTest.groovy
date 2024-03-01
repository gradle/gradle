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
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import spock.lang.Issue

class SuiteTimestampIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2730")
    void "test logging is included in XML results"() {
        file("build.gradle") << """
            apply plugin: 'java'
                ${mavenCentralRepository()}
                dependencies { testImplementation '$testJunitCoordinates' }
        """

        file("src/test/java/SomeTest.java") << """
import org.junit.*;

public class SomeTest {
    @Test public void foo() {
        System.out.println("foo");
    }
}
"""
        when:
        run "test"

        then:
        new JUnitXmlTestExecutionResult(testDirectory).testClass("SomeTest").withResult { testClassNode ->
            testClassNode.@timestamp.toString().startsWith(new GregorianCalendar().get(Calendar.YEAR).toString())
        }
    }
}
