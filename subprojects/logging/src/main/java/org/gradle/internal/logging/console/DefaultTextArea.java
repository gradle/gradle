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

import org.gradle.api.Action;
import org.gradle.internal.logging.text.AbstractLineChoppingStyledTextOutput;

public class DefaultTextArea extends AbstractLineChoppingStyledTextOutput implements TextArea {
    private static final Action<AnsiContext> NEW_LINE_ACTION = new Action<AnsiContext>() {
        @Override
        public void execute(AnsiContext ansi) {
            ansi.newLine();
        }
    };
    private static final int CHARS_PER_TAB_STOP = 8;
    private final Cursor writePos = new Cursor();
    private final AnsiExecutor ansiExecutor;

    public DefaultTextArea(AnsiExecutor ansiExecutor) {
        this.ansiExecutor = ansiExecutor;
    }

    public Cursor getWritePosition() {
        return writePos;
    }

    public void newLineAdjustment() {
        writePos.row++;
    }

    @Override
    protected void doLineText(final CharSequence text) {
        if (text.length() == 0) {
            return;
        }

        ansiExecutor.writeAt(writePos, new Action<AnsiContext>() {
            @Override
            public void execute(AnsiContext ansi) {
                ansi.withStyle(getStyle(), new Action<AnsiContext>() {
                    @Override
                    public void execute(AnsiContext ansi) {
                        String textStr = text.toString();
                        int pos = 0;
                        while (pos < text.length()) {
                            int next = textStr.indexOf('\t', pos);
                            if (next == pos) {
                                int charsToNextStop = CHARS_PER_TAB_STOP - (writePos.col % CHARS_PER_TAB_STOP);
                                for(int i = 0; i < charsToNextStop; i++) {
                                    ansi.a(" ");
                                }
                                pos++;
                            } else if (next > pos) {
                                ansi.a(textStr.substring(pos, next));
                                pos = next;
                            } else {
                                ansi.a(textStr.substring(pos, textStr.length()));
                                pos = textStr.length();
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void doEndLine(CharSequence endOfLine) {
        ansiExecutor.writeAt(writePos, NEW_LINE_ACTION);
    }
}
