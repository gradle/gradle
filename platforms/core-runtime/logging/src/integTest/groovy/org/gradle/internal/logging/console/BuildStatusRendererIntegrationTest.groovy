/*
 * Copyright 2024 Gradle and contributors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.NavigationBarColorization
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class BuildStatusRendererIntegrationTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Subject
    BuildStatusRenderer renderer

    def "colorizes navigation bar levels when enabled"() {
        given:
        renderer = new BuildStatusRenderer(ConsoleOutput.Rich, NavigationBarColorization.ON)
        def rootId = new OperationIdentifier(1)
        def childId = new OperationIdentifier(2)
        def grandchildId = new OperationIdentifier(3)

        when:
        renderer.onStart(createProgressEvent(rootId, BuildOperationCategory.CONFIGURE_ROOT_BUILD))
        renderer.onStart(createProgressEvent(childId, BuildOperationCategory.CONFIGURE_PROJECT))
        renderer.onStart(createProgressEvent(grandchildId, BuildOperationCategory.TASK))

        then:
        renderer.formatOperation("Root", rootId).contains("[36m") // Cyan
        renderer.formatOperation("Child", childId).contains("[32m") // Green
        renderer.formatOperation("Grandchild", grandchildId).contains("[33m") // Yellow
    }

    def "respects AUTO colorization setting"() {
        given:
        renderer = new BuildStatusRenderer(consoleOutput, NavigationBarColorization.AUTO)
        def opId = new OperationIdentifier(1)
        renderer.onStart(createProgressEvent(opId, BuildOperationCategory.TASK))

        expect:
        renderer.formatOperation("Test", opId).contains("[") == shouldColorize

        where:
        consoleOutput        | shouldColorize
        ConsoleOutput.Plain  | false
        ConsoleOutput.Rich   | true
        ConsoleOutput.Auto   | false // Because test environment doesn't have a terminal
    }

    def "maintains correct level after operation completion"() {
        given:
        renderer = new BuildStatusRenderer(ConsoleOutput.Rich, NavigationBarColorization.ON)
        def rootId = new OperationIdentifier(1)
        def childId = new OperationIdentifier(2)

        when:
        renderer.onStart(createProgressEvent(rootId, BuildOperationCategory.CONFIGURE_ROOT_BUILD))
        renderer.onStart(createProgressEvent(childId, BuildOperationCategory.CONFIGURE_PROJECT))
        renderer.onComplete(childId)

        and:
        def newChildId = new OperationIdentifier(3)
        renderer.onStart(createProgressEvent(newChildId, BuildOperationCategory.CONFIGURE_PROJECT))

        then:
        renderer.formatOperation("NewChild", newChildId).contains("[32m") // Green (level 1)
    }

    private ProgressStartEvent createProgressEvent(OperationIdentifier id, BuildOperationCategory category) {
        return new ProgressStartEvent(
            id,
            null,
            System.currentTimeMillis(),
            category.toString(),
            "Test Operation",
            "Test Header",
            "Test Status",
            0,
            false,
            null,
            category
        )
    }
} 