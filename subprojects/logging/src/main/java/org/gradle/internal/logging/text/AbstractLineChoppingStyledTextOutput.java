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

package org.gradle.internal.logging.text;

import org.gradle.api.Action;
import org.gradle.internal.SystemProperties;

/**
 * A {@link StyledTextOutput} that breaks text up into lines.
 */
public abstract class AbstractLineChoppingStyledTextOutput extends AbstractStyledTextOutput {
    private final char[] eolChars;
    private final String eol;
    private SeenFromEol seenFromEol;
    private State currentState = INITIAL_STATE;

    protected AbstractLineChoppingStyledTextOutput() {
        eol = SystemProperties.getInstance().getLineSeparator();
        eolChars = eol.toCharArray();
        seenFromEol = new SeenFromEol(eolChars);
    }

    @Override
    protected final void doAppend(String text) {
        StateContext context = new StateContext(text);

        while (context.hasChar()) {
            currentState.execute(context);
        }
        seenFromEol = context.seenFromEol;
        context.flushLineText();
    }

    /**
     * Called before text is about to be appended to the start of a line.
     */
    protected void doStartLine() {
    }

    /**
     * Called when text is to be appended. Does not include any end-of-line separators.
     */
    protected abstract void doLineText(CharSequence text);

    /**
     * Called when end of line is to be appended.
     */
    protected abstract void doEndLine(CharSequence endOfLine);

    private interface State extends Action<StateContext> {}

    private class StateContext {
        private final String text;
        private final int max;
        private final SeenFromEol seenFromEol = AbstractLineChoppingStyledTextOutput.this.seenFromEol;
        private final char[] eolChars = AbstractLineChoppingStyledTextOutput.this.eolChars;
        private final String eol = AbstractLineChoppingStyledTextOutput.this.eol;

        private int start;
        private int pos;

        StateContext(String text) {
            this.text = text;
            this.max = text.length();
            this.pos = -seenFromEol.size();
            this.start = pos;
        }

        void next() {
            pos++;
        }

        void next(int count) {
            pos += count;
        }

        char currentChar() {
            return text.charAt(pos + seenFromEol.size());
        }

        boolean hasChar() {
            return pos + seenFromEol.size() < max;
        }

        void setState(State state) {
            currentState = state;
        }

        void reset() {
            start = pos;
            seenFromEol.clear();
        }

        void flushLineText() {
            // Left over data from previous append is only possible when a multi-chars new line is
            // been processed and split across multiple append calls.
            if (start < pos) {
                String data = "";
                // Flushing data split across previous and current appending
                if (start < 0 && pos >= 0) {
                    data = seenFromEol.string(0, Math.abs(start)) + text.substring(0, pos);
                // Flushing data coming only from current appending
                } else if (start >= 0) {
                    data = text.substring(start, pos);
                }

                if (data.length() > 0) {
                    doLineText(data);
                }
            }
        }

        void flushEndLine(String eol) {
            doEndLine(eol);
        }

        void flushStartLine() {
            doStartLine();
        }
    }

    private static final State SYSTEM_EOL_PARSING_STATE = new State() {
        @Override
        public void execute(StateContext context) {
            if (!context.seenFromEol.all()) {
                boolean windows = context.eol.equals("\r\n");

                if (!windows && context.currentChar() == context.eolChars[context.seenFromEol.size()]) {
                    context.seenFromEol.add(context.currentChar());
                    if (context.seenFromEol.all()) {
                        context.flushLineText();
                        context.flushEndLine(context.eol);
                        context.next(context.seenFromEol.size());
                        context.reset();
                        context.setState(START_LINE_STATE);
                    }
                    return;
                }

                if (context.seenFromEol.none()) {
                    if (context.currentChar() == '\r') {
                        context.seenFromEol.add('\r');
                        context.setState(WINDOWS_EOL_PARSING_STATE);
                        return;
                    }
                    if (context.currentChar() == '\n') {
                        context.flushLineText();
                        context.flushEndLine("\n");
                        context.next();
                        context.reset();
                        context.setState(START_LINE_STATE);
                        return;
                    }
                    context.next();
                    return;
                }
            }

            context.next(context.seenFromEol.size());
            context.flushLineText();
            context.reset();
        }
    };

    private static final State INITIAL_STATE = SYSTEM_EOL_PARSING_STATE;

    private static final State WINDOWS_EOL_PARSING_STATE = new State() {
        @Override
        public void execute(StateContext context) {


            if (context.currentChar() == '\n') {
                context.flushLineText();
                context.flushEndLine("\r\n");
                context.next(2);
                context.reset();
                context.setState(START_LINE_STATE);
            } else if (context.currentChar() == '\r') {
                context.next();
            } else {
                context.next();
                context.seenFromEol.clear();
                context.setState(INITIAL_STATE);
            }
        }
    };

    private static final State START_LINE_STATE = new State() {
        @Override
        public void execute(StateContext context) {
            context.flushStartLine();
            context.setState(INITIAL_STATE);
        }
    };

    private static class SeenFromEol {

        private final char[] seen;
        private int count;

        SeenFromEol(char[] eol) {
            this.seen = new char[eol.length];
            this.count = 0;
        }

        public void add(char c) {
            seen[count++] = c;
        }

        void clear() {
            count = 0;
        }

        int size() {
            return count;
        }

        boolean all() {
            return count == seen.length;
        }

        boolean none() {
            return count == 0;
        }

        public String string(int from, int to) {
            return new String(seen, from, to - from);
        }
    }
}
