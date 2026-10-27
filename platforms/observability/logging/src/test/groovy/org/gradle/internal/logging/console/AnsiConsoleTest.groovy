/*
 * Copyright 2026 the original author or authors.
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

import net.rubygrapefruit.ansi.AnsiParser
import net.rubygrapefruit.ansi.token.NewLine
import net.rubygrapefruit.ansi.token.Text
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Issue
import spock.lang.Specification

import net.rubygrapefruit.ansi.console.AnsiConsole as Screen

class AnsiConsoleTest extends Specification {
    def output = new StringBuilder()
    def consoleMetaData = Stub(ConsoleMetaData) {
        getCols() >> 80
    }
    def console = new AnsiConsole(output, {} as Flushable, new TestColorMap(), consoleMetaData, true)

    @Issue("https://github.com/gradle/gradle/issues/39584")
    def "erases the old text under the status bar when output is written and worker lines are added in the same update"() {
        given:
        def area = console.buildProgressArea
        area.resizeBuildProgressTo(1)
        area.visible = true
        console.statusBar.text = "[##.............] 14% EXECUTING [2s]"
        area.buildProgressLabels[0].text = "> :prepareTheEnvironmentOfTheParallelTasks"
        console.flush()

        when:
        console.buildOutputArea.println("Environment ready")
        area.resizeBuildProgressTo(3)
        area.buildProgressLabels[0].text = "> :work1"
        area.buildProgressLabels[1].text = "> :work2"
        area.buildProgressLabels[2].text = "> :work3"
        console.flush()

        then:
        screen() == [
            "Environment ready",
            "[##.............] 14% EXECUTING [2s]",
            "> :work1",
            "> :work2",
            "> :work3",
        ]
    }

    private List<String> screen() {
        def screen = new Screen()
        def parser = new AnsiParser().newParser("utf-8", screen)
        parser.write(output.toString().getBytes("utf-8"))
        parser.flush()
        def text = new StringBuilder()
        screen.contents { token ->
            if (token instanceof Text) {
                text.append(token.text)
            } else if (token instanceof NewLine) {
                text.append("\n")
            }
        }
        return text.toString().readLines()
    }
}
