/*
 * Copyright 2017 the original author or authors.
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

import org.fusesource.jansi.Ansi
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import spock.lang.Issue
import spock.lang.Specification

class MultiLineBuildProgressAreaTest extends Specification {
    def ansi = Mock(Ansi)
    def factory = new AnsiFactory() {
        Ansi create() {
            return ansi
        }
    }
    def writeCursor = new Cursor()
    def target = Stub(Appendable)
    def colorMap = new TestColorMap()
    def consoleMetaData = Mock(ConsoleMetaData)
    def newLineListener = Mock(DefaultAnsiExecutor.NewLineListener)
    def ansiExecutor = new DefaultAnsiExecutor(target, colorMap, factory, consoleMetaData, writeCursor, newLineListener)
    def progressArea = new MultiLineBuildProgressArea()

    def setup() {
        progressArea.resizeBuildProgressTo(4);
        newLineListener.beforeNewLineWritten(_, _) >> {
            progressArea.newLineAdjustment();
        }

        consoleMetaData.cols >> Integer.MAX_VALUE

        progressArea.visible = true
    }

    def "scrolls the console with new lines when redrawing an empty work in progress area"() {
        when:
        progressArea.setVisible(true)
        redraw()

        then:
        interaction {
            (progressArea.getHeight() - 1) * ansi.newline()
            0 * ansi._
        }
    }

    def "redraw the work in progress area"() {
        given:
        fillArea()

        when:
        redraw()

        then:
        interaction {
            (progressArea.getHeight() - 1) * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw()
            0 * ansi._
        }
    }

    def "execute the minimum ansi action when updating a label in the work in progress area between redraw"() {
        given:
        fillArea()

        when:
        redraw()
        progressArea.buildProgressLabels[1].text = "Progress 1 > new information"
        redraw()

        then:
        interaction {
            (progressArea.getHeight() - 1) * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw()

            // Update progress label 1
            1 * ansi.cursorUp(3)
            1 * ansi.a("Progress 1 > new information")
            1 * ansi.cursorLeft(28)
            1 * ansi.cursorDown(3)
            0 * ansi._
        }
    }

    def "redraws the entire work in progress area when scrolling between redraw"() {
        given:
        fillArea()

        when:
        redraw()
        progressArea.scrollDownBy(2)
        redraw()

        then:
        interaction {
            (progressArea.getHeight() - 1) * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw()

            2 * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw()
            0 * ansi._
        }
    }

    def "clears the end of the line when the area is scrolled and a label is updated with a smaller text between redraw"() {
        given:
        fillArea()

        when:
        redraw()
        progressArea.scrollDownBy(2)
        int i = 0
        for (StyledLabel label : progressArea.buildProgressLabels) {
            label.text = "Small " + i++
        }
        redraw()

        then:
        interaction {
            (progressArea.getHeight() - 1) * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw()

            2 * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw("Small")

            (progressArea.buildProgressLabels.size() - 2) * ansi.eraseLine(Ansi.Erase.FORWARD)
            0 * ansi._
        }
    }

    def "doesn't do any ansi calls when visibility is set to false before the first redraw"() {
        given:
        fillArea()

        when:
        progressArea.setVisible(false)
        redraw()

        then:
        0 * ansi._
    }

    def "doesn't scroll the area when visibility is set to false"() {
        given:
        fillArea()
        def absoluteDeltaRow = 2

        when:
        redraw()
        progressArea.setVisible(false)
        progressArea.scrollDownBy(absoluteDeltaRow)
        redraw()

        then:
        interaction {
            (progressArea.getHeight() - 1) * ansi.newline()
            1 * ansi.cursorUp(5)

            expectAreaRedraw()

            def absoluteDeltaRowToAreaTop = progressArea.getBuildProgressLabels().size() + 1 - absoluteDeltaRow

            1 * ansi.cursorUp(absoluteDeltaRowToAreaTop)
            1 * ansi.eraseLine(Ansi.Erase.ALL)
            1 * ansi.cursorDown(1)
            1 * ansi.eraseLine(Ansi.Erase.ALL)
            1 * ansi.cursorDown(1)
            1 * ansi.eraseLine(Ansi.Erase.ALL)

            // Parking
            1 * ansi.cursorDown(1)
            1 * ansi.eraseLine(Ansi.Erase.ALL)

            1 * ansi.cursorUp(absoluteDeltaRowToAreaTop)
            0 * ansi._
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/1482")
    def "doesn't move the write cursor when progress area was never visible while out of bound"() {
        given:
        progressArea.visible = false
        progressArea.scrollDownBy(1)

        when:
        redraw()

        then:
        progressArea.writePosition.row < 0
        0 * ansi._
    }

    def "doesn't move the write cursor when progress area was never visible"() {
        given:
        progressArea.visible = false
        writeCursor.row = 4  // Not on origin row

        when:
        redraw()

        then:
        writeCursor.row == 4
        0 * ansi._

    }

    def "doesn't move the write cursor when progress area turn invisible while out of bound"() {
        given:
        progressArea.visible = false
        progressArea.scrollUpBy(progressArea.height - 1)
        redraw()  // Ensure visible

        when:
        progressArea.scrollDownBy(progressArea.height)
        writeCursor.row = 2  // Not on origin row
        redraw()

        then:
        writeCursor.row == 2
        progressArea.writePosition.row == -1
        0 * ansi._
    }

    def "resize build progress area makes the new build label available at the end of the area"() {
        given:
        def increaseBy = 4
        def oldBuildProgressLabels = new ArrayList<StyledLabel>(progressArea.buildProgressLabels)
        def currentCount = progressArea.buildProgressLabels.size()
        def newCount = currentCount + increaseBy

        when:
        progressArea.resizeBuildProgressTo(newCount)
        fillArea()

        then:
        progressArea.buildProgressLabels.size() == newCount
        oldBuildProgressLabels.eachWithIndex{ StyledLabel entry, int i ->
            assert progressArea.buildProgressLabels.get(i) == entry
        }
    }

    void redraw() {
        ansiExecutor.write {
            progressArea.redraw(it)
        }
    }

    void fillArea(String prefix = "Progress") {
        progressArea.progressBar.text = "progress bar"
        for (int i = 0; i < progressArea.buildProgressLabels.size(); ++i) {
            String text = String.format("%s %d", prefix, i)
            progressArea.buildProgressLabels.get(i).text = text
        }
    }

    void expectAreaRedraw(String prefix = "Progress") {
        // Progress bar
        1 * ansi.a("progress bar")

        // Progress label
        1 * ansi.cursorLeft(12)
        1 * ansi.cursorDown(1)

        for (int i = 0; i < progressArea.buildProgressLabels.size(); ++i) {
            String text = String.format("%s %d", prefix, i)
            1 * ansi.a(text)
            1 * ansi.cursorLeft(text.length())
            1 * ansi.cursorDown(1)
        }
    }
}
