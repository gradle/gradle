/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.SystemProperties;

/**
 * A {@link org.gradle.logging.StyledTextOutput} that breaks text up into lines.
 */
public abstract class AbstractLineChoppingStyledTextOutput extends AbstractStyledTextOutput {
    private final char[] eol;
    private int seenCharsFromEol;

    protected AbstractLineChoppingStyledTextOutput() {
        eol = SystemProperties.getLineSeparator().toCharArray();
    }

    @Override
    protected final void doAppend(String text) {
        int max = text.length();
        int pos = 0;
        int start = 0;
        while (pos < max) {
            if (seenCharsFromEol == eol.length) {
                doStartLine();
                seenCharsFromEol = 0;
            }
            if (seenCharsFromEol < eol.length && text.charAt(pos) == eol[seenCharsFromEol]) {
                seenCharsFromEol++;
                pos++;
                if (seenCharsFromEol == eol.length) {
                    doLineText(text.substring(start, pos), true);
                    doFinishLine();
                    start = pos;
                }
            } else {
                seenCharsFromEol = 0;
                pos++;
            }
        }
        if (pos > start) {
            doLineText(text.substring(start, pos), false);
        }
    }

    /**
     * Called <em>after</em> the end-of-line text has been appended.
     */
    protected void doFinishLine() {
    }

    /**
     * Called before text is about to be appended to the start of a line.
     */
    protected void doStartLine() {
    }

    /**
     * Called when text is to be appended.
     * @param text The text, includes any end-of-line terminator
     * @param terminatesLine true if the given text terminates a line (including the end-of-line string).
     */
    protected abstract void doLineText(CharSequence text, boolean terminatesLine);
}
