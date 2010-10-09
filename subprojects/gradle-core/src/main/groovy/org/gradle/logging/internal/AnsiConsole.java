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

import org.apache.commons.lang.StringUtils;
import org.fusesource.jansi.Ansi;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.logging.StyledTextOutput;

import java.io.Flushable;
import java.io.IOException;
import java.util.Iterator;

public class AnsiConsole implements Console {
    private final static String EOL = System.getProperty("line.separator");
    private final Appendable target;
    private final Flushable flushable;
    private LabelImpl statusBar;
    private final TextAreaImpl textArea;
    private final Screen container;
    private final ColorMap colorMap;

    public AnsiConsole(Appendable target, Flushable flushable, ColorMap colorMap) {
        this.target = target;
        this.flushable = flushable;
        this.colorMap = colorMap;
        container = new Screen();
        textArea = new TextAreaImpl(container);
    }

    public Label getStatusBar() {
        if (statusBar == null) {
            statusBar = new LabelImpl(container);
            render(new Action<Ansi>() {
                public void execute(Ansi ansi) {
                    textArea.onDeactivate(ansi);
                    statusBar.onActivate(ansi);
                }
            });
        }
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
         * Called when this widget becomes the active widget. The active widget is the widget at the bottom of the
         * screen. When called, the cursor will be positioned at the left edge of bottom-most line of the screen.
         */
        void onActivate(Ansi ansi);

        /**
         * Called when this widget is no longer the active widget. Should Remove content of this widget from the last
         * line of the screen and leave the cursor at left edge of bottom-most line.
         */
        void onDeactivate(Ansi ansi);
    }

    private class Screen implements Container {
        public void redraw(Widget widget, final Action<Ansi> drawOperation) {
            if (widget == textArea) {
                render(new Action<Ansi>() {
                    public void execute(Ansi ansi) {
                        if (statusBar != null) {
                            statusBar.onDeactivate(ansi);
                            textArea.onActivate(ansi);
                        }
                        drawOperation.execute(ansi);
                        if (statusBar != null) {
                            textArea.onDeactivate(ansi);
                            statusBar.onActivate(ansi);
                        }
                    }
                });
            } else {
                assert widget == statusBar;
                render(new Action<Ansi>() {
                    public void execute(Ansi ansi) {
                        drawOperation.execute(ansi);
                    }
                });
            }
        }

        public void close(Widget widget) {
            if (widget == textArea) {
                throw new UnsupportedOperationException();
            }
            if (widget == statusBar) {
                render(new Action<Ansi>() {
                    public void execute(Ansi ansi) {
                        statusBar.onDeactivate(ansi);
                        textArea.onActivate(ansi);
                        statusBar = null;
                    }
                });
            }
        }
    }

    private class LabelImpl implements Label, Widget {
        private final Container container;
        private String text = "";
        private String displayedText = "";

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

        public void onDeactivate(Ansi ansi) {
            if (displayedText.length() > 0) {
                ansi.cursorLeft(displayedText.length());
                ansi.eraseLine(Ansi.Erase.FORWARD);
                displayedText = "";
            }
        }

        public void onActivate(Ansi ansi) {
            draw(ansi);
        }

        public void draw(Ansi ansi) {
            String prefix = StringUtils.getCommonPrefix(new String[]{text, displayedText});
            if (prefix.length() < displayedText.length()) {
                ansi.cursorLeft(displayedText.length() - prefix.length());
            }
            if (prefix.length() < text.length()) {
                ColorMap.Color color = colorMap.getStatusBarColor();
                color.on(ansi);
                ansi.a(text.substring(prefix.length()));
                color.off(ansi);
            }
            if (displayedText.length() > text.length()) {
                ansi.eraseLine(Ansi.Erase.FORWARD);
            }
            displayedText = text;
        }
    }

    private class TextAreaImpl extends AbstractStyledTextOutput implements TextArea, Widget {
        private final Container container;
        private int width;
        boolean extraEol;
        StyledTextOutput.Style style = Style.Normal;

        private TextAreaImpl(Container container) {
            this.container = container;
        }

        public void onDeactivate(Ansi ansi) {
            if (width > 0) {
                ansi.newline();
                extraEol = true;
            }
        }

        public void onActivate(Ansi ansi) {
            if (extraEol) {
                ansi.cursorUp(1);
                ansi.cursorRight(width);
                extraEol = false;
            }
        }

        @Override
        public StyledTextOutput style(Style style) {
            this.style = style;
            return this;
        }

        @Override
        protected void doAppend(final String text) {
            if (text.length() == 0) {
                return;
            }
            container.redraw(this, new Action<Ansi>() {
                public void execute(Ansi ansi) {
                    ColorMap.Color color = colorMap.getColourFor(style);
                    color.on(ansi);

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

                    color.off(ansi);
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
