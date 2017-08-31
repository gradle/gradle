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

import org.fusesource.jansi.Ansi;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.text.Style;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;

import java.io.IOException;

public class DefaultAnsiExecutor implements AnsiExecutor {
    private static final NewLineListener NO_OP_LISTENER = new NoOpListener();
    private final Appendable target;
    private final ColorMap colorMap;
    private final AnsiFactory factory;
    private final ConsoleMetaData consoleMetaData;
    private final NewLineListener listener;
    private final Cursor writeCursor;

    public DefaultAnsiExecutor(Appendable target, ColorMap colorMap, AnsiFactory factory, ConsoleMetaData consoleMetaData) {
        this(target, colorMap, factory, consoleMetaData, new Cursor());
    }

    public DefaultAnsiExecutor(Appendable target, ColorMap colorMap, AnsiFactory factory, ConsoleMetaData consoleMetaData, Cursor writeCursor) {
        this(target, colorMap, factory, consoleMetaData, writeCursor, NO_OP_LISTENER);
    }

    public DefaultAnsiExecutor(Appendable target, ColorMap colorMap, AnsiFactory factory, ConsoleMetaData consoleMetaData, Cursor writeCursor, NewLineListener listener) {
        this.target = target;
        this.colorMap = colorMap;
        this.factory = factory;
        this.consoleMetaData = consoleMetaData;
        this.writeCursor = writeCursor;
        this.listener = listener;
    }

    @Override
    public void write(Action<? super AnsiContext> action) {
        Ansi ansi = factory.create();
        action.execute(new AnsiContextImpl(ansi, colorMap, writeCursor));
        write(ansi);
    }

    @Override
    public void writeAt(Cursor writePos, Action<? super AnsiContext> action) {
        Ansi ansi = factory.create();
        positionCursorAt(writePos, ansi);
        action.execute(new AnsiContextImpl(ansi, colorMap, writePos));
        write(ansi);
    }

    private void charactersWritten(Cursor cursor, int count) {
        writeCursor.col += count;
        cursor.copyFrom(writeCursor);
    }

    private void newLineWritten(Cursor cursor) {
        writeCursor.col = 0;

        // On any line except the bottom most one, a new line simply move the cursor to the next row.
        // Note: the next row has a lower index.
        if (writeCursor.row > 0) {
            writeCursor.row--;
        } else {
            writeCursor.row = 0;
        }
        cursor.copyFrom(writeCursor);
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

    private void write(Ansi ansi) {
        try {
            target.append(ansi.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    interface NewLineListener {
        void beforeNewLineWritten(AnsiContext ansi, Cursor writeCursor);

        void beforeLineWrap(AnsiContext ansi, Cursor writeCursor);
        void afterLineWrap(AnsiContext ansi, Cursor writeCursor);
    }

    private static class NoOpListener implements NewLineListener {
        @Override
        public void beforeNewLineWritten(AnsiContext ansi, Cursor writeCursor) {

        }

        @Override
        public void beforeLineWrap(AnsiContext ansi, Cursor writeCursor) {

        }

        @Override
        public void afterLineWrap(AnsiContext ansi, Cursor writeCursor) {

        }
    }

    private class AnsiContextImpl implements AnsiContext {
        private final Ansi delegate;
        private final ColorMap colorMap;
        private final Cursor writePos;

        AnsiContextImpl(Ansi delegate, ColorMap colorMap, Cursor writePos) {
            this.delegate = delegate;
            this.colorMap = colorMap;
            this.writePos = writePos;
        }

        @Override
        public AnsiContext withColor(ColorMap.Color color, Action<? super AnsiContext> action) {
            color.on(delegate);
            action.execute(this);
            color.off(delegate);
            return this;
        }

        @Override
        public AnsiContext withStyle(Style style, Action<? super AnsiContext> action) {
            if (Style.NORMAL.equals(style)) {
                action.execute(this);
                return this;
            }
            return withColor(colorMap.getColourFor(style), action);
        }

        @Override
        public AnsiContext withStyle(StyledTextOutput.Style style, Action<? super AnsiContext> action) {
            return withColor(colorMap.getColourFor(style), action);
        }

        @Override
        public AnsiContext a(CharSequence value) {
            if (value.length() > 0) {
                int cols = consoleMetaData.getCols();

                int numberOfWrapBefore = (cols > 0) ? writeCursor.col / (cols + 1) : 0;
                delegate.a(value);
                charactersWritten(writePos, value.length());
                int numberOfWrapAfter = (cols > 0) ? writeCursor.col / (cols + 1) : 0;

                int numberOfWrap = numberOfWrapAfter - numberOfWrapBefore;
                if (numberOfWrap > 0) {
                    while (numberOfWrap-- > 0) {
                        listener.beforeLineWrap(this, Cursor.at(writePos.row, cols));
                        if (writePos.row != 0) {
                            --writePos.row;
                        }
                        if (writeCursor.row != 0) {
                            --writeCursor.row;  // We don't adjust the column value as in the event we unwrap, we want to keep correctness
                        }
                    }

                    int col = writeCursor.col % cols;
                    listener.afterLineWrap(this, Cursor.at(writePos.row, col));
                }
            }
            return this;
        }

        @Override
        public AnsiContext newLines(int numberOfNewLines) {
            while(0 < numberOfNewLines--) {
                newLine();
            }
            return this;
        }

        @Override
        public AnsiContext newLine() {
            int cols = consoleMetaData.getCols();
            int col = (cols > 0) ? writeCursor.col % cols : 0;
            listener.beforeNewLineWritten(this, Cursor.at(writeCursor.row, col));
            delegate.newline();
            newLineWritten(writePos);
            return this;
        }

        @Override
        public AnsiContext eraseForward() {
            delegate.eraseLine(Ansi.Erase.FORWARD);
            return this;
        }

        @Override
        public AnsiContext eraseAll() {
            delegate.eraseLine(Ansi.Erase.ALL);
            return this;
        }

        @Override
        public AnsiContext cursorAt(Cursor cursor) {
            positionCursorAt(cursor, delegate);
            return this;
        }

        @Override
        public AnsiContext writeAt(Cursor writePos) {
            positionCursorAt(writePos, delegate);
            return new AnsiContextImpl(delegate, colorMap, writePos);
        }
    }
}
