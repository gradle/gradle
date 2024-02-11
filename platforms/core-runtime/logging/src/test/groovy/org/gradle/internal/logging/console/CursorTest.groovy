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

import spock.lang.Specification

class CursorTest extends Specification {
    private static final def ROWS = [0, 2, 42, -1, -5]
    private static final def COLS = [0, 2, 21, 0, -3]

    def "create a cursor at (0,0) when using factory method 'newBottomLeft'"() {
        when:
        Cursor cursor = Cursor.newBottomLeft()

        then:
        cursor.row == 0
        cursor.col == 0
    }

    def "create a cursor at (#row, #col) when using factory method 'at'"() {
        when:
        Cursor cursor = Cursor.at(row, col)

        then:
        cursor.row == row
        cursor.col == col

        where:
        row << ROWS
        col << COLS
    }

    def "create a copy of a cursor when using factory method 'from'"() {
        given:
        Cursor cursor = Cursor.at(row, col)

        when:
        Cursor copyCursor = Cursor.from(cursor)

        then:
        System.identityHashCode(cursor) != System.identityHashCode(copyCursor)
        copyCursor.row == cursor.row
        copyCursor.col == cursor.col

        where:
        row << ROWS
        col << COLS
    }

    def "move cursor to (0,0) when calling bottomLeft"() {
        given:
        Cursor cursor = Cursor.at(row, col)

        when:
        cursor.bottomLeft()

        then:
        cursor.row == 0
        cursor.col == 0

        where:
        row << ROWS
        col << COLS
    }

    def "move cursor to (#row,#col) when calling copyFrom on another cursor"() {
        given:
        Cursor cursor = Cursor.at(row, col)

        when:
        Cursor newCursor = new Cursor()
        newCursor.copyFrom(cursor)

        then:
        newCursor.row == cursor.row
        newCursor.col == cursor.col

        where:
        row << ROWS
        col << COLS
    }
}
