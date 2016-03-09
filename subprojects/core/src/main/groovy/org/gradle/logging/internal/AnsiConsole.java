/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging.internal;

import org.fusesource.jansi.Ansi;
import org.gradle.api.UncheckedIOException;

import java.io.Flushable;
import java.io.IOException;

public class AnsiConsole implements Console {
    private final Appendable target;
    private final Flushable flushable;
    private final LabelImpl statusBar;
    private final TextAreaImpl textArea;
    private final ColorMap colorMap;
    private final boolean forceAnsi;
    private final Cursor writeCursor = new Cursor();
    private final Cursor textCursor = new Cursor();
    private final Cursor statusBarCursor = new Cursor();

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap) {
        this(target, flushable, colorMap, false);
    }

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap, boolean forceAnsi) {
        this.target = target;
        this.flushable = flushable;
        this.colorMap = colorMap;
        textArea = new TextAreaImpl(textCursor);
        statusBar = new LabelImpl(statusBarCursor);
        this.forceAnsi = forceAnsi;
    }

    @Override
    public void flush() {
        statusBar.redraw();
        try {
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Ansi createAnsi() {
        if (forceAnsi) {
            return new Ansi();
        } else {
            return Ansi.ansi();
        }
    }

    private void positionCursorAt(Cursor position, Ansi ansi) {
        if (writeCursor.row == position.row) {
            if (writeCursor.col == position.col) {
                return;
            }
            if (writeCursor.col < position.col) {
                ansi.cursorRight(position.col - writeCursor.col);
            } else {
                ansi.cursorLeft(writeCursor.col - position.col);
            }
        } else {
            if (writeCursor.col > 0) {
                ansi.cursorLeft(writeCursor.col);
            }
            if (writeCursor.row < position.row) {
                ansi.cursorUp(position.row - writeCursor.row);
            } else {
                ansi.cursorDown(writeCursor.row - position.row);
            }
            if (position.col > 0) {
                ansi.cursorRight(position.col);
            }
        }
        writeCursor.copyFrom(position);
    }

    private void charactersWritten(Cursor cursor, int count) {
        writeCursor.col += count;
        cursor.copyFrom(writeCursor);
    }

    private void newLineWritten(Cursor cursor) {
        writeCursor.col = 0;
        if (writeCursor.row > 0) {
            writeCursor.row--;
        } else {
            writeCursor.row = 0;
            textCursor.row++;
            statusBarCursor.row++;
        }
        cursor.copyFrom(writeCursor);
    }

    private void write(Ansi ansi) {
        try {
            target.append(ansi.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Label getStatusBar() {
        return statusBar;
    }

    public TextArea getMainArea() {
        return textArea;
    }

    private class Cursor {
        int col; // count from left of screen, 0 = left most
        int row; // count from bottom of screen, 0 = bottom most, 1 == 2nd from bottom

        public void copyFrom(Cursor position) {
            this.col = position.col;
            this.row = position.row;
        }

        public void bottomLeft() {
            col = 0;
            row = 0;
        }
    }

    private class LabelImpl implements Label {
        private final Cursor writePos;
        private String writtenText = "";
        private String text = "";

        public LabelImpl(Cursor writePos) {
            this.writePos = writePos;
        }

        public void setText(String text) {
            if (text.equals(this.text)) {
                return;
            }
            this.text = text;
        }

        public void redraw() {
            boolean hasTextOnBottomLine = textCursor.row == 0 && textCursor.col > 0;
            if (writePos.row == 0 && writtenText.equals(text) && !hasTextOnBottomLine) {
                // Does not need to be redrawn
                return;
            }
            Ansi ansi = createAnsi();
            if (hasTextOnBottomLine) {
                writePos.copyFrom(textCursor);
                positionCursorAt(writePos, ansi);
                // TODO - remove old status text
                ansi.newline();
                newLineWritten(writePos);
                writtenText = "";
            } else {
                writePos.bottomLeft();
                positionCursorAt(writePos, ansi);
            }
            if (text.length() > 0) {
                ColorMap.Color color = colorMap.getStatusBarColor();
                color.on(ansi);
                ansi.a(text);
                color.off(ansi);
            }
            if (text.length() < writtenText.length()) {
                ansi.eraseLine(Ansi.Erase.FORWARD);
            }
            write(ansi);
            charactersWritten(writePos, text.length());
            writtenText = text;
        }
    }

    private class TextAreaImpl extends AbstractLineChoppingStyledTextOutput implements TextArea {
        private final Cursor writePos;

        public TextAreaImpl(Cursor writePos) {
            this.writePos = writePos;
        }

        @Override
        protected void doLineText(final CharSequence text, final boolean terminatesLine) {
            if (text.length() == 0) {
                return;
            }
            Ansi ansi = createAnsi();
            positionCursorAt(writePos, ansi);
            if (writePos.row == statusBarCursor.row && statusBarCursor.col > writePos.col) {
                // TODO - clear only remaining status line content, if any
                ansi.eraseLine(Ansi.Erase.FORWARD);
            }
            ColorMap.Color color = colorMap.getColourFor(getStyle());
            color.on(ansi);
            ansi.a(text.toString());
            color.off(ansi);
            write(ansi);
            if (terminatesLine) {
                newLineWritten(writePos);
            } else {
                charactersWritten(writePos, text.length());
            }
        }
    }
}
