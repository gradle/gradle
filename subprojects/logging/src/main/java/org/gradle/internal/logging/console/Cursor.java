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

package org.gradle.internal.logging.console;

/**
 * A virtual console screen cursor. This class avoid complex screen position management.
 */
public class Cursor {
    int col; // count from left of screen, 0 = left most
    int row; // count from bottom of screen, 0 = bottom most, 1 == 2nd from bottom

    public void copyFrom(Cursor position) {
        if (position == this) {
            return;
        }
        this.col = position.col;
        this.row = position.row;
    }

    public void bottomLeft() {
        col = 0;
        row = 0;
    }

    public static Cursor at(int row, int col) {
        Cursor result = new Cursor();
        result.row = row;
        result.col = col;
        return result;
    }

    public static Cursor newBottomLeft() {
        Cursor result = new Cursor();
        result.bottomLeft();
        return result;
    }

    public static Cursor from(Cursor position) {
        Cursor result = new Cursor();
        result.copyFrom(position);
        return result;
    }
}
