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
import spock.lang.Specification

class DefaultAnsiExecutorTest extends Specification {
    private static final int TERMINAL_WIDTH = 5;
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

    def "writing a long line that wraps will callback the listener"() {
        given:
        consoleMetaData.cols >> TERMINAL_WIDTH
        Cursor writePos = Cursor.at(3, 0)
        int startRow = writePos.row
        String text = "A" * TERMINAL_WIDTH +
            "B" * TERMINAL_WIDTH +
            "C" * TERMINAL_WIDTH +
            "D" * 3

        when:
        ansiExecutor.writeAt(writePos) {
            it.a(text)
        }

        then:
        writeCursor == Cursor.at(0, text.length())
        writePos == writeCursor
        interaction { expectLineWrapCallback(startRow, text.length()) }
        0 * newLineListener._
    }

    def "completing a line wrapping will callback the listener"() {
        given:
        consoleMetaData.cols >> TERMINAL_WIDTH
        Cursor writePos = Cursor.at(1, 0)
        int startRow = writePos.row
        String text = "A" * TERMINAL_WIDTH + "B" * 2

        when:
        ansiExecutor.writeAt(writePos) {
            it.a(text.substring(0, 3))
            it.a(text.substring(3))
        }

        then:
        writeCursor == Cursor.at(0, text.length())
        writePos == writeCursor
        interaction { expectLineWrapCallback(startRow, text.length()) }
        0 * newLineListener._
    }

    def "a full non wrapping line won't callback the listener"() {
        given:
        consoleMetaData.cols >> TERMINAL_WIDTH
        Cursor writePos = Cursor.newBottomLeft();
        int startRow = writePos.row
        String text = "A" * TERMINAL_WIDTH

        when:
        ansiExecutor.writeAt(writePos) {
            it.a(text)
        }

        then:
        writeCursor == Cursor.at(startRow, TERMINAL_WIDTH)
        writePos == writeCursor
        0 * newLineListener._
    }

    def "a new line after a line wrap will callback the listener with a column value equals to the number of char left from the wrap"() {
        given:
        consoleMetaData.cols >> TERMINAL_WIDTH
        Cursor writePos = Cursor.at(1, 0)
        int startRow = writePos.row
        String text = "A" * TERMINAL_WIDTH + "B" * 3

        when:
        ansiExecutor.writeAt(writePos) {
            it.a(text)
            it.newLine()
        }

        then:
        writeCursor == Cursor.newBottomLeft()
        writePos == writeCursor
        interaction { expectLineWrapCallback(startRow, text.length()) }
        1 * newLineListener.beforeNewLineWritten(_, Cursor.at(0, 3))
        0 * newLineListener._
    }

    def "a new line in a zero width terminal doesn't throw exception"() {
        given:
        consoleMetaData.cols >> 0
        Cursor writePos = Cursor.at(1, 0)
        int startRow = writePos.row

        when:
        ansiExecutor.writeAt(writePos) {
            it.newLine()
        }

        then:
        noExceptionThrown()
        writeCursor == Cursor.newBottomLeft()
        writePos == writeCursor
        1 * newLineListener.beforeNewLineWritten(_, Cursor.at(startRow, 0))
        0 * newLineListener._
    }

    def expectLineWrapCallback(int writtenRow, int writtenLength) {
        int numberOfWrap = writtenLength / (TERMINAL_WIDTH + 1)
        while (numberOfWrap-- > 0) {
            1 * newLineListener.beforeLineWrap(_, toExpectedCursor(writtenRow--, TERMINAL_WIDTH))
        }
        int col = writtenLength % TERMINAL_WIDTH
        1 * newLineListener.afterLineWrap(_, toExpectedCursor(writtenRow, col))
    }

    Cursor toExpectedCursor(int row, int col) {
        Cursor.at(Math.max(0, row), col);
    }
}
