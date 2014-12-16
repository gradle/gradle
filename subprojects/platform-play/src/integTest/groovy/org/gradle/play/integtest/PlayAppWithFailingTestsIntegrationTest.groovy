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

package org.gradle.play.integtest

import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult
import org.gradle.play.integtest.fixtures.MultiPlayVersionIntegrationTest
import org.gradle.play.integtest.fixtures.app.PlayApp
import org.gradle.play.integtest.fixtures.app.WithFailingTestsApp
import org.gradle.util.TextUtil

class PlayAppWithFailingTestsIntegrationTest extends MultiPlayVersionIntegrationTest {

    PlayApp playApp = new WithFailingTestsApp();

    def setup(){
        playApp.writeSources(testDirectory.file("."))
    }

    def "reports failing run play app tests"() {
        when:
        fails("testPlayBinary")
        then:

        output.contains(TextUtil.toPlatformLineSeparators("""
FailingApplicationSpec > Application should::render the index page FAILED
    org.specs2.reporter.SpecFailureAssertionFailedError
"""))

        output.contains(TextUtil.toPlatformLineSeparators("""
FailingIntegrationSpec > Application should::work from within a browser FAILED
    org.specs2.reporter.SpecFailureAssertionFailedError
"""))
        errorOutput.contains("6 tests completed, 2 failed")
        errorOutput.contains("> There were failing tests.")

        def result = new JUnitXmlTestExecutionResult(testDirectory, "build/playBinary/reports/test/xml")
        result.assertTestClassesExecuted("ApplicationSpec", "IntegrationSpec", "FailingApplicationSpec", "FailingIntegrationSpec")
        result.testClass("ApplicationSpec").assertTestCount(2, 0, 0)
        result.testClass("IntegrationSpec").assertTestCount(1, 0, 0)
        result.testClass("FailingIntegrationSpec").assertTestCount(1, 1, 0)
        result.testClass("FailingApplicationSpec").assertTestCount(2, 1, 0)
    }
}