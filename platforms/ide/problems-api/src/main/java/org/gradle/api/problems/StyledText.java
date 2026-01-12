/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Represents styled text that can be rendered in different formats (plain text, HTML).
 *
 * @since 9.4.0
 */
@Incubating
public class StyledText implements Serializable {

    private final List<Span> spans;

    /**
     * Creates a {@link StyledText} with the given spans.
     *
     * @param spans the list of spans
     * @since 9.4.0
     */
    public StyledText(List<Span> spans) {
        this.spans = spans;
    }

    /**
     * Creates a plain text {@link StyledText}.
     * @param text the plain text
     * @since 9.4.0
     */
    public static StyledText text(String text) {
        return new StyledText(Collections.singletonList(new Span(text, Style.TEXT)));
    }

    /**
     * Creates a code styled {@link StyledText}.
     * @param text the code text
     * @since 9.4.0
     */
    public static StyledText code(String text) {
        return new StyledText(Collections.singletonList(new Span(text, Style.CODE)));
    }

    /**
     * Appends plain text to this {@link StyledText}.
     *
     * @param text the text
     * @return a new {@link StyledText} with the appended text
     * @since 9.4.0
     */
    public StyledText appendText(String text) {
        // create a new list from spans and add the new span
        return new StyledText(copyAndAppend(spans, text, Style.TEXT));
    }

    /**
     * Appends code styled text to this {@link StyledText}.
     *
     * @param code the code text
     * @return a new {@link StyledText} with the appended code text
     * @since 9.4.0
     */
    public StyledText appendCode(String code) {
        return new StyledText(copyAndAppend(spans, code, Style.CODE));
    }

    private static List<Span> copyAndAppend(List<Span> spans, String text, Style style) {
        List<Span> newSpans = new java.util.ArrayList<>(spans);
        newSpans.add(new Span(text, style));
        return Collections.unmodifiableList(newSpans);
    }

    /**
     * Returns the list of spans.
     *
     * @return the list of spans
     * @since 9.4.0
     */
    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public String toString() {
        return toPlainString();
    }

    /**
     * Returns the plain text representation of this {@link StyledText}.
     *
     * @return the plain text representation
     * @since 9.4.0
     */
    public String toPlainString() {
        StringBuilder builder = new StringBuilder();
        for (Span span : spans) {
            builder.append(span.getText());
        }
        return builder.toString();
    }

    /**
     * Returns the HTML representation of this {@link StyledText}.
     * @return the HTML representation
     * @since 9.4.0
     */
    public String toHtmlString() {
        StringBuilder builder = new StringBuilder();
        for (Span span : spans) {
            if (span.getStyle() == Style.CODE) {
                builder.append("<b>").append(escapeHtml(span.getText())).append("</b>");
            } else {
                builder.append(escapeHtml(span.getText()));
            }
        }
        return builder.toString();
    }

    private static String escapeHtml(String text) {
        // TODO (donat) Use a proper HTML escaping library
        return text;
    }

    /**
     * The style of a span.
     * @since 9.4.0
     */
    @Incubating
    public enum Style {
        /**
         * Plain text style.
         * @since 9.4.0
         */
        TEXT,

        /**
         * Code style.
         * @since 9.4.0
         */
        CODE
    }

    /**
     * A span of text with a specific style.
     * @since 9.4.0
     */
    @Incubating
    public static class Span implements Serializable {
        private final String text;
        private final Style style;

        /**
         * Creates a span with the given text and style.
         * @param text the text
         * @param style the style
         * @since 9.4.0
         */
        public Span(String text, Style style) {
            this.text = text;
            this.style = style;
        }

        /**
         * Returns the text of this span.
         * @return the text
         * @since 9.4.0
         */
        public String getText() {
            return text;
        }

        /**
         * Returns the style of this span.
         * @return the style
         * @since 9.4.0
         */
        public Style getStyle() {
            return style;
        }
    }
}
