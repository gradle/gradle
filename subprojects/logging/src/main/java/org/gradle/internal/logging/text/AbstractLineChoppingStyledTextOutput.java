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

import org.gradle.internal.SystemProperties;

/**
 * A {@link StyledTextOutput} that breaks text up into lines.
 */
public abstract class AbstractLineChoppingStyledTextOutput extends AbstractStyledTextOutput {
    private final char[] eolChars;
    private final String eol;
    private int seenCharsFromEol;

    protected AbstractLineChoppingStyledTextOutput() {
        eol = SystemProperties.getInstance().getLineSeparator();
        eolChars = eol.toCharArray();
    }

    @Override
    protected final void doAppend(String text) {
        int max = text.length();
        int pos = 0;
        int start = 0;
        while (pos < max) {
            if (seenCharsFromEol == eolChars.length) {
                doStartLine();
                seenCharsFromEol = 0;
            }
            if (seenCharsFromEol < eolChars.length && text.charAt(pos) == eolChars[seenCharsFromEol]) {
                seenCharsFromEol++;
                pos++;
                if (seenCharsFromEol == eolChars.length) {
                    if (start < pos - seenCharsFromEol) {
                        doLineText(text.substring(start,  pos - seenCharsFromEol));
                    }
                    doEndLine(eol);
                    start = pos;
                }
            } else {
                if (seenCharsFromEol > 0 && start == 0) {
                    doLineText(eol.substring(0, seenCharsFromEol));
                    start = pos;
                }
                seenCharsFromEol = 0;
                pos++;
            }
        }
        if (start < pos - seenCharsFromEol) {
            doLineText(text.substring(start,  pos - seenCharsFromEol));
        }
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
}
