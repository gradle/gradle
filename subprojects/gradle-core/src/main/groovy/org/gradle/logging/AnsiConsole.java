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

package org.gradle.logging;

import org.fusesource.jansi.Ansi;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;

import java.io.Flushable;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

public class AnsiConsole implements Console {
    private final static String EOL = System.getProperty("line.separator");
    private final Appendable target;
    private final Flushable flushable;
    private final LinkedList<LabelImpl> statusBars = new LinkedList<LabelImpl>();
    private final TextAreaImpl textArea;
    private Widget bottomWidget;
    private final Screen container;

    public AnsiConsole(Appendable target, Flushable flushable) {
        this.target = target;
        this.flushable = flushable;
        container = new Screen();
        textArea = new TextAreaImpl(container);
        bottomWidget = textArea;
    }

    public Label addStatusBar() {
        final LabelImpl statusBar = new LabelImpl(container);
        render(new Action<Ansi>() {
            public void execute(Ansi ansi) {
                bottomWidget.removeFromLastLine(ansi);
                statusBar.draw(ansi);
            }
        });
        statusBars.addFirst(statusBar);
        bottomWidget = statusBar;
        return statusBar;
    }

    public TextArea getMainArea() {
        return textArea;
    }

    private void render(Action<Ansi> action) {
        Ansi ansi = createAnsi();
        action.execute(ansi);
        try {
            target.append(ansi.toString());
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Ansi createAnsi() {
        return Ansi.ansi();
    }

    private interface Container {
        void redraw(Widget widget, Action<Ansi> drawOperation);

        void close(Widget widget);
    }

    private interface Widget {
        /**
         * Removes content of this widget from the last line of the screen. Leaves cursor at left edge of bottom-most
         * line.
         */
        void removeFromLastLine(Ansi ansi);
    }

    private class Screen implements Container {
        public void redraw(Widget widget, final Action<Ansi> drawOperation) {
            final LabelImpl currentStatusBar = statusBars.peek();
            if (widget == textArea) {
                render(new Action<Ansi>() {
                    public void execute(Ansi ansi) {
                        if (currentStatusBar != null) {
                            currentStatusBar.removeFromLastLine(ansi);
                        }
                        drawOperation.execute(ansi);
                        if (currentStatusBar != null) {
                            textArea.removeFromLastLine(ansi);
                            currentStatusBar.draw(ansi);
                        }
                    }
                });
            } else {
                final LabelImpl statusBar = (LabelImpl) widget;
                if (statusBar != currentStatusBar) {
                    return;
                }
                render(new Action<Ansi>() {
                    public void execute(Ansi ansi) {
                        statusBar.removeFromLastLine(ansi);
                        drawOperation.execute(ansi);
                    }
                });
            }
        }

        public void close(Widget widget) {
            if (widget == textArea) {
                throw new UnsupportedOperationException();
            }
            final LabelImpl statusBar = (LabelImpl) widget;
            statusBars.remove(statusBar);
            if (statusBar == bottomWidget) {
                render(new Action<Ansi>() {
                    public void execute(Ansi ansi) {
                        statusBar.removeFromLastLine(ansi);
                        LabelImpl current = statusBars.peek();
                        if (current != null) {
                            current.draw(ansi);
                            bottomWidget = current;
                        } else {
                            bottomWidget = textArea;
                        }
                    }
                });
            }
        }
    }

    private class LabelImpl implements Label, Widget {
        private final Container container;
        private String text = "";
        private int width;

        public LabelImpl(Container container) {
            this.container = container;
        }

        public void setText(String text) {
            if (text.equals(this.text)) {
                return;
            }
            this.text = text;
            container.redraw(this, new Action<Ansi>() {
                public void execute(Ansi ansi) {
                    draw(ansi);
                }
            });
        }

        public void close() {
            container.close(this);
        }

        public void removeFromLastLine(Ansi ansi) {
            if (width > 0) {
                ansi.cursorLeft(width);
                ansi.eraseLine(Ansi.Erase.FORWARD);
            }
        }

        public void draw(Ansi ansi) {
            width = text.length();
            if (width > 0) {
                ansi.a(text);
            }
        }
    }

    private class TextAreaImpl implements TextArea, Widget {
        private final Container container;
        private int width;
        boolean extraEol;

        private TextAreaImpl(Container container) {
            this.container = container;
        }

        public void removeFromLastLine(Ansi ansi) {
            if (width > 0) {
                ansi.newline();
                extraEol = true;
            }
        }

        public void append(final CharSequence text) {
            if (text.length() == 0) {
                return;
            }
            container.redraw(this, new Action<Ansi>() {
                public void execute(Ansi ansi) {
                    if (extraEol) {
                        ansi.cursorUp(1);
                        ansi.cursorRight(width);
                        extraEol = false;
                    }

                    Iterator<String> tokenizer = new LineSplitter(text);
                    while (tokenizer.hasNext()) {
                        String token = tokenizer.next();
                        if (token.equals(EOL)) {
                            width = 0;
                            extraEol = false;
                        } else {
                            width += token.length();
                        }
                        ansi.a(token);
                    }
                }
            });
        }
    }

    private static class LineSplitter implements Iterator<String> {
        private final CharSequence text;
        private int start;
        private int end;

        private LineSplitter(CharSequence text) {
            this.text = text;
            findNext();
        }

        public boolean findNext() {
            if (end == text.length()) {
                start = -1;
                return false;
            }
            if (startsWithEol(text, end)) {
                start = end;
                end = start + EOL.length();
                return true;
            }
            int pos = end;
            while (pos < text.length()) {
                if (startsWithEol(text, pos)) {
                    start = end;
                    end = pos;
                    return true;
                }
                pos++;
            }
            start = end;
            end = text.length();
            return true;
        }

        private boolean startsWithEol(CharSequence text, int startAt) {
            if (startAt + EOL.length() > text.length()) {
                return false;
            }
            for (int i = 0; i < EOL.length(); i++) {
                if (EOL.charAt(i) != text.charAt(startAt + i)) {
                    return false;
                }
            }
            return true;
        }

        public boolean hasNext() {
            return start >= 0;
        }

        public String next() {
            CharSequence next = text.subSequence(start, end);
            findNext();
            return next.toString();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
