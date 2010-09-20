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

/**
 * Provides streaming of styled text, that is, a stream of text and styling information. Implementations are not
 * required to be thread-safe.
 */
public interface StyledTextOutput extends Appendable {
    enum Style {
        /**
         * Regular text.
         */
        Normal,
        /**
         * A header.
         */
        Header,
        /**
         * User input
         */
        UserInput,
        /**
         * An identifier for something
         */
        Identifier,
        /**
         * The description of something
         */
        Description,
        /**
         * Operation progress status
         */
        ProgressStatus,
        /**
         * Some failure message
         */
        Failure,
        /**
         * General purpose informational text
         */
        Info,
        /**
         * General purpose error text
         */
        Error
    }

    /**
     * Appends a character with the current style.
     *
     * @param c The character
     * @return this
     */
    StyledTextOutput append(char c);

    /**
     * Appends a sequence of characters with the current style.
     *
     * @param csq The character sequence
     * @return this.
     */
    StyledTextOutput append(CharSequence csq);

    /**
     * Appends a sequence of characters with the current style.
     *
     * @param csq The character sequence
     * @return this.
     */
    StyledTextOutput append(CharSequence csq, int start, int end);

    /**
     * Switches to a new style.
     *
     * @param style The style.
     * @return this
     */
    StyledTextOutput style(Style style);

    /**
     * Appends text with the current style.
     *
     * @param text The text
     * @return this
     */
    StyledTextOutput text(Object text);

    /**
     * Appends text with the current style and starts a new line.
     *
     * @param text The text
     * @return this
     */
    StyledTextOutput println(Object text);

    /**
     * Appends a formatted string with the current style.
     *
     * @param pattern The pattern string
     * @param args The args for the pattern
     * @return this
     */
    StyledTextOutput format(String pattern, Object... args);

    /**
     * Appends a formatted string with the current style and starts a new line.
     *
     * @param pattern The pattern string
     * @param args The args for the pattern
     * @return this
     */
    StyledTextOutput formatln(String pattern, Object... args);

    /**
     * Starts a new line.
     *
     * @return this
     */
    StyledTextOutput println();
}
