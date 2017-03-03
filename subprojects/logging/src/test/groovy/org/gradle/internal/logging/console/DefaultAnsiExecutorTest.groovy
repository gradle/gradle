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
        consoleMetaData.cols >> {5}

        when:
        ansiExecutor.writeAt(Cursor.at(3, 0)) {
            it.a("AAAAABBBBBCCCCCDDD")
        }

        then:
        1 * newLineListener.beforeLineWrap(_, Cursor.at(3, 5))
        1 * newLineListener.beforeLineWrap(_, Cursor.at(2, 5))
        1 * newLineListener.beforeLineWrap(_, Cursor.at(1, 5))
        1 * newLineListener.afterLineWrap(_, Cursor.at(0, 3))
        0 * newLineListener._
    }

    def "completing a line wrapping will callback the listener"() {
        given:
        consoleMetaData.cols >> {5}

        when:
        ansiExecutor.writeAt(Cursor.at(1, 0)) {
            it.a("AAA")
            it.a("AABB")
        }

        then:
        1 * newLineListener.beforeLineWrap(_, Cursor.at(1, 5))
        1 * newLineListener.afterLineWrap(_, Cursor.at(0, 2))
        0 * newLineListener._
    }
}
