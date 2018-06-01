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
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest
import org.gradle.play.integtest.fixtures.app.WithFailingTestsApp
import org.gradle.util.VersionNumber
import org.junit.Assume

class PlayAppWithFailingTestsIntegrationTest extends PlayMultiVersionIntegrationTest {

    PlayApp playApp = new WithFailingTestsApp(oldVersion: isOldVersion())

    def setup() {
        playApp.writeSources(testDirectory)
        buildFile << """
model {
    components {
        play {
            targetPlatform "play-${version}"
        }
    }
}
"""
    }

    def "reports failing run play app tests"() {
        Assume.assumeTrue(versionNumber < VersionNumber.parse("2.6.2"))
        when:
        fails("testPlayBinary")
        then:

        output.contains """
FailingApplicationSpec > failingTest FAILED
    java.lang.AssertionError at FailingApplicationSpec.scala:23
"""

        output.contains """
FailingIntegrationSpec > failingTest FAILED
    java.lang.AssertionError at FailingIntegrationSpec.scala:23
"""
        failure.assertHasErrorOutput("6 tests completed, 2 failed")
        failure.assertHasCause("There were failing tests.")

        def result = new JUnitXmlTestExecutionResult(testDirectory, "build/playBinary/reports/test/xml")
        result.assertTestClassesExecuted("ApplicationSpec", "IntegrationSpec", "FailingApplicationSpec", "FailingIntegrationSpec")
        result.testClass("ApplicationSpec").assertTestCount(2, 0, 0)
        result.testClass("IntegrationSpec").assertTestCount(1, 0, 0)
        result.testClass("FailingIntegrationSpec").assertTestCount(1, 1, 0)
        result.testClass("FailingApplicationSpec").assertTestCount(2, 1, 0)
    }
}
