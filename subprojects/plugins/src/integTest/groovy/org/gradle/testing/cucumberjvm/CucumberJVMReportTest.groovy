/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.testing.cucumberjvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Timeout
import org.gradle.util.TestPrecondition

class CucumberJVMReportTest extends AbstractIntegrationSpec {

    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-2739")
    @Requires(TestPrecondition.NON_JDK5)
    def testReportingSupportsCucumberStepsWithSlashes() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies {
               testCompile "junit:junit:4.11"
               testCompile "info.cukes:cucumber-java:1.1.2"
               testCompile "info.cukes:cucumber-junit:1.1.2"
            }
            test {
               testLogging.showStandardStreams = true
               testLogging.events  'started', 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
               reports.junitXml.enabled = true
               reports.html.enabled = true
            }
        """
        when:
        run "test"

        println testDirectory.absolutePath
        then:
        ":test" in nonSkippedTasks
        and:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("RunCukesTest", "Scenario: Say hello /two/three")
        result.testClass("Scenario: Say hello /two/three").assertTestPassed("Given I have a hello app with Howdy and /four")
    }
}
