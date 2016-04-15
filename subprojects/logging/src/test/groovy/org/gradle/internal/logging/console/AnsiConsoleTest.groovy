/*
 * Copyright 2016 the original author or authors.
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
import org.fusesource.jansi.Ansi.Color
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.text.StyledTextOutput
import spock.lang.Specification

class AnsiConsoleTest extends Specification {
    private static final String EOL = SystemProperties.instance.lineSeparator

    def ansi = Mock(Ansi)
    def target = Stub(Appendable)
    def flushable = Stub(Flushable)
    def colorMap = new TestColorMap()
    def console = new AnsiConsole(target, flushable, colorMap) {
        def Ansi createAnsi() {
            return ansi
        }
    }

    def appendsTextToMainArea() {
        when:
        console.mainArea.append('message')

        then:
        1 * ansi.a('message')
        0 * ansi._

        when:
        console.mainArea.append("message2${EOL}message3")

        then:
        1 * ansi.a('message2')
        1 * ansi.newline()
        1 * ansi.a('message3')
        0 * ansi._
    }

    def appendsStyledTextToMainArea() {
        when:
        console.mainArea.withStyle(StyledTextOutput.Style.Header).append('message')
        console.mainArea.append("message2")

        then:
        1 * ansi.fg(Color.YELLOW)
        1 * ansi.a('message')
        1 * ansi.fg(Color.DEFAULT)
        1 * ansi.a('message2')
        0 * ansi._
    }

    def appendsStyledTextWithTabsToMainArea() {
        when:
        console.mainArea.withStyle(StyledTextOutput.Style.Header).append('12\t3')
        console.mainArea.append("\t4\t\t")

        then:
        1 * ansi.fg(Color.YELLOW)
        1 * ansi.a('12')
        6 * ansi.a(' ')
        1 * ansi.a('3')
        1 * ansi.fg(Color.DEFAULT)
        7 * ansi.a(' ')
        1 * ansi.a('4')
        15 * ansi.a(' ')
        0 * ansi._
    }

    def flushDisplaysStatusBarWithNonEmptyText() {
        when:
        console.statusBar.text = 'text'
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('text')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def flushDisplaysStatusBarWhenTextInMainArea() {
        when:
        console.mainArea.append("message${EOL}")
        console.statusBar.text = 'text'
        console.flush()

        then:
        1 * ansi.a('message')
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('text')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def flushDoesNothingWhenStatusBarAndTextAreEmpty() {
        when:
        console.statusBar.text = ''
        console.flush()

        then:
        0 * ansi._
    }

    def flushDoesNotShowStatusBarWhenStatusBarIsEmpty() {
        when:
        console.statusBar.text = ''
        console.mainArea.append("message${EOL}")
        console.flush()

        then:
        1 * ansi.a('message')
        1 * ansi.newline()
        0 * ansi._
    }

    def flushRedrawsStatusBarWhenTextChangesValue() {
        def statusBar = console.statusBar

        when:
        statusBar.text = '123'
        console.flush()
        statusBar.text = 'abc'
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('123')
        1 * ansi.a(Ansi.Attribute.RESET)
        1 * ansi.cursorLeft(3)
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('abc')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def flushRedrawsStatusBarWhenTextAdded() {
        def statusBar = console.statusBar

        when:
        statusBar.text = '123'
        console.flush()
        statusBar.text = '123456'
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('123')
        1 * ansi.a(Ansi.Attribute.RESET)
        1 * ansi.cursorLeft(3)
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('123456')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def flushRedrawsStatusBarWhenTextRemoved() {
        def statusBar = console.statusBar

        when:
        statusBar.text = '123456'
        console.flush()
        statusBar.text = '123'
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('123456')
        1 * ansi.a(Ansi.Attribute.RESET)
        1 * ansi.cursorLeft(6)
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('123')
        1 * ansi.a(Ansi.Attribute.RESET)
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        0 * ansi._
    }

    def flushRedrawsStatusBarWhenTextSetToEmpty() {
        def statusBar = console.statusBar

        when:
        statusBar.text = '123456'
        console.flush()
        statusBar.text = ''
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('123456')
        1 * ansi.a(Ansi.Attribute.RESET)
        1 * ansi.cursorLeft(6)
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        0 * ansi._
    }

    def appendsTextWhenStatusBarIsPresent() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append("message1$EOL");
        console.mainArea.append("message2$EOL");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.a('message1')
        1 * ansi.newline()
        1 * ansi.a('message2')
        1 * ansi.newline()
        0 * ansi._

        when:
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def appendsTextWhenStatusBarIsPresentAndLongerThanNewLine() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append("12");
        console.mainArea.append("34$EOL");
        console.mainArea.append("$EOL$EOL");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.a('12')
        1 * ansi.a('34')
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        1 * ansi.newline()
        1 * ansi.newline()
        1 * ansi.newline()
        0 * ansi._

        when:
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def appendsTextContainingTabCharsWhenStatusBarIsPresent() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append("\t");
        console.mainArea.append("12\t345\t6$EOL");

        then:
        1 * ansi.cursorLeft(6)
        8 * ansi.a(' ')
        1 * ansi.a('12')
        6 * ansi.a(' ')
        1 * ansi.a('345')
        5 * ansi.a(' ')
        1 * ansi.a('6')
        1 * ansi.newline()
        0 * ansi._

        when:
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def appendsTextWithNoEOLWhenStatusBarIsPresent() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append("message1");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.a('message1')
        0 * ansi._

        when:
        console.flush()

        then:
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._

        when:
        console.mainArea.append("message2");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.cursorUp(1)
        1 * ansi.cursorRight(8)
        1 * ansi.a('message2')
        0 * ansi._

        when:
        console.flush()

        then:
        0 * ansi._ // no update required
    }

    def appendsTextWithTabsAndNoEOLWhenStatusBarIsPresent() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append("\t12\t3\t");

        then:
        1 * ansi.cursorLeft(6)
        8 * ansi.a(' ')
        1 * ansi.a('12')
        6 * ansi.a(' ')
        1 * ansi.a('3')
        7 * ansi.a(' ')
        0 * ansi._

        when:
        console.flush()

        then:
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._

        when:
        console.mainArea.append("456");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.cursorUp(1)
        1 * ansi.cursorRight(24)
        1 * ansi.a('456')
        0 * ansi._

        when:
        console.flush()

        then:
        0 * ansi._ // no update required
    }

    def appendsTextWithNoEOLWhenStatusBarIsPresentAndLongerThanNewTextLines() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append("a");
        console.mainArea.append("b");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.a('a')
        1 * ansi.a('b')
        0 * ansi._

        when:
        console.flush()

        then:
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._

        when:
        console.mainArea.append("c");

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.cursorUp(1)
        1 * ansi.cursorRight(2)
        1 * ansi.a('c')
        0 * ansi._

        when:
        console.flush()

        then:
        0 * ansi._ // no update required
    }

    def appendsTextAfterEmptyLineWhenStatusBarIsPresent() {
        given:
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.mainArea.append(EOL);
        console.flush()

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._

        when:
        console.mainArea.append("message");
        console.flush()

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.a("message")
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def updatesStatusBarValueWhenNoTrailingEOLInMainText() {
        given:
        console.statusBar.text = 'status'
        console.mainArea.append("message1");
        console.flush()
        console.mainArea.append("message2");
        console.flush()

        when:
        console.statusBar.text = 'new'
        console.flush()

        then:
        1 * ansi.cursorLeft(16)
        1 * ansi.cursorDown(1)
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('new')
        1 * ansi.a(Ansi.Attribute.RESET)
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        0 * ansi._
    }

    def addsStatusBarWhenNoTrailingEOLInMainArea() {
        given:
        console.mainArea.append('message')
        console.flush()

        when:
        console.statusBar.text = 'status'
        console.flush()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('status')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }

    def removesStatusBarWhenNoTrailingEOLInMainArea() {
        given:
        console.mainArea.append('message1')
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.statusBar.text = ''
        console.flush()

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        0 * ansi._

        when:
        console.mainArea.append('message2')
        console.flush()

        then:
        1 * ansi.cursorUp(1)
        1 * ansi.cursorRight(8)
        1 * ansi.a('message2')
        0 * ansi._
    }

    def coalescesMultipleUpdates() {
        given:
        console.mainArea.append('message1')
        console.statusBar.text = 'status'
        console.flush()

        when:
        console.statusBar.text = ''
        console.mainArea.append('message2')
        console.statusBar.text = '1'
        console.statusBar.text = '2'
        console.mainArea.append('message3')
        console.mainArea.append(EOL)
        console.mainArea.append('message4')
        console.statusBar.text = '3'
        console.flush()

        then:
        1 * ansi.cursorLeft(6)
        1 * ansi.cursorUp(1)
        1 * ansi.cursorRight(8)
        1 * ansi.a('message2')
        1 * ansi.a('message3')
        1 * ansi.newline()
        1 * ansi.a('message4')
        1 * ansi.newline()
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('3')
        1 * ansi.a(Ansi.Attribute.RESET)
        0 * ansi._
    }
}
