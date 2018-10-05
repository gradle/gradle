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
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.play.integtest.fixtures.PlayMultiVersionApplicationIntegrationTest

abstract class PlayTestApplicationIntegrationTest extends PlayMultiVersionApplicationIntegrationTest {
    def "can run play app tests"() {
        when:
        succeeds("check")
        then:
        executed(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayTwirlTemplates",
                ":compilePlayBinaryScala",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":compilePlayBinaryTests",
                ":testPlayBinary")

        then:
        verifyTestOutput(new JUnitXmlTestExecutionResult(testDirectory, "build/playBinary/reports/test/xml"))

        when:
        succeeds("check")
        then:
        skipped(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayTwirlTemplates",
                ":compilePlayBinaryScala",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":compilePlayBinaryTests",
                ":testPlayBinary")
    }

    def "can run play app tests if java plugin is applied"() {
        when:
        buildFile << """
            apply plugin: 'java'
        """
        succeeds("check")
        then:
        executed(
            ":compilePlayBinaryPlayRoutes",
            ":compilePlayBinaryPlayTwirlTemplates",
            ":compilePlayBinaryScala",
            ":createPlayBinaryJar",
            ":createPlayBinaryAssetsJar",
            ":playBinary",
            ":compilePlayBinaryTests",
            ":testPlayBinary")

        then:
        verifyTestOutput(new JUnitXmlTestExecutionResult(testDirectory, "build/playBinary/reports/test/xml"))
    }

    void verifyTestOutput(TestExecutionResult result) { }
}
