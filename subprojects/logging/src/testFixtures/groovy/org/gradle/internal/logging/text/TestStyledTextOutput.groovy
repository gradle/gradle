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

package org.gradle.internal.logging.text

import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.text.StyledTextOutput.Style

class TestStyledTextOutput extends AbstractStyledTextOutput {
    StringBuilder result = new StringBuilder()

    @Override
    String toString() {
        result.toString()
    }

    TestStyledTextOutput ignoreStyle() {
        return new TestStyledTextOutput() {
            @Override protected void doStyleChange(Style style) {
            }
        }
    }

    String getRawValue() {
        return result.toString()
    }

    /**
     * Returns the normalized value of this text output. Normalizes:
     * - style changes to {style} where _style_ is the lowercase name of the style.
     * - line endings to \n
     * - stack traces to {stacktrace}\n
     */
    String getValue() {
        StringBuilder normalised = new StringBuilder()

        String eol = SystemProperties.instance.lineSeparator
        boolean inStackTrace = false
        new StringTokenizer(result.toString().replaceAll(eol, '\n'), '\n', true).each { String line ->
            if (line == '\n') {
                if (!inStackTrace) {
                    normalised.append('\n')
                }
            } else if (line.matches(/\s+at .+\(.+\)/) || line.matches(/\s+\.\.\. \d+ more/)) {
                if (!inStackTrace) {
                    normalised.append('{stacktrace}\n')
                }
                inStackTrace = true
            } else {
                inStackTrace = false
                normalised.append(line)
            }
        }
        return normalised.toString()
    }

    @Override
    protected void doStyleChange(Style style) {
        result.append("{${style.toString().toLowerCase()}}")
    }

    @Override
    protected void doAppend(String text) {
        result.append(text)
    }
}
