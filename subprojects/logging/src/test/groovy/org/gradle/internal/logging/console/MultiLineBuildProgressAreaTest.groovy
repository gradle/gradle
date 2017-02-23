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
import spock.lang.Ignore
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
    def newLineListener = Mock(DefaultAnsiExecutor.NewLineListener)
    def ansiExecutor = new DefaultAnsiExecutor(target, colorMap, factory, writeCursor, newLineListener)
    def progressArea = new MultiLineBuildProgressArea(4)

    def setup() {
        newLineListener.beforeNewLineWritten(_) >> {
            progressArea.newLineAdjustment();
        }
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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    @Ignore
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

    void redraw() {
        ansiExecutor.write {
            progressArea.redraw(it)
        }
    }

    void fillArea() {
        progressArea.progressBar.text = "progress bar"
        progressArea.buildProgressLabels[0].text = "Progress 0"
        progressArea.buildProgressLabels[1].text = "Progress 1"
        progressArea.buildProgressLabels[2].text = "Progress 2"
        progressArea.buildProgressLabels[3].text = "Progress 3"
    }

    void expectAreaRedraw(String prefix = "Progress") {
        // Progress bar
        1 * ansi.a("progress bar")

        // Progress label
        1 * ansi.cursorLeft(12)
        1 * ansi.cursorDown(1)

        for (int i = 0; i < progressArea.getBuildProgressLabels().size(); ++i) {
            String text = String.format("%s %d", prefix, i)
            1 * ansi.a(text)
            1 * ansi.cursorLeft(text.length())
            1 * ansi.cursorDown(1)
        }
    }
}
