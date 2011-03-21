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
package org.gradle.logging.internal

import org.gradle.logging.StyledTextOutput.Style
import org.gradle.util.SystemProperties

class AbstractStyledTextOutputTest extends OutputSpecification {
    private final TestStyledTextOutput output = new TestStyledTextOutput()

    def onOutputWritesText() {
        when:
        output.onOutput('some message')

        then:
        output.value == 'some message'
    }

    def writesNullText() {
        when:
        output.text(null)

        then:
        output.value == 'null'
    }

    def writesEndOfLine() {
        when:
        output.println()

        then:
        output.rawValue == SystemProperties.lineSeparator
    }

    def appendsCharacter() {
        when:
        output.append('c' as char)

        then:
        output.value == 'c'
    }

    def appendsCharSequence() {
        when:
        output.append('some message')

        then:
        output.value == 'some message'
    }

    def appendsNullCharSequence() {
        when:
        output.append(null)

        then:
        output.value == 'null'
    }

    def appendsCharSubsequence() {
        when:
        output.append('some message', 5, 9)

        then:
        output.value == 'mess'
    }

    def appendsNullCharSubsequence() {
        when:
        output.append(null, 5, 9)

        then:
        output.value == 'null'
    }

    def printlnWritesTextAndEndOfLine() {
        when:
        output.println('message')

        then:
        output.value == 'message\n'
    }

    def formatsText() {
        when:
        output.format('[%s]', 'message')

        then:
        output.value == '[message]'
    }

    def formatsTextAndEndOfLine() {
        when:
        output.formatln('[%s]', 'message')

        then:
        output.value == '[message]\n'
    }

    def formatsException() {
        when:
        output.exception(new RuntimeException('broken'))

        then:
        output.value == 'java.lang.RuntimeException: broken\n{stacktrace}\n'
    }

    def canMixInStyleInformation() {
        when:
        output.style(Style.Info).text('info test').style(Style.Normal)

        then:
        output.value == '{info}info test{normal}'
    }

    def ignoresStyleChangeWhenAlreadyUsingTheGivenStyle() {
        when:
        output.style(Style.Info).text('info test').style(Style.Info)

        then:
        output.value == '{info}info test'
    }

    def writesTextWithTemporaryStyleChange() {
        when:
        output.style(Style.Info).withStyle(Style.Error).text('some text')

        then:
        output.value == '{info}{error}some text{info}'
    }

    def writesTextAndEndOfLineWithTemporaryStyleChange() {
        when:
        output.style(Style.Info).withStyle(Style.Error).println('some text')

        then:
        output.value == '{info}{error}some text{info}\n'
    }

    def appendsTextWithTemporaryStyleChange() {
        when:
        output.style(Style.Info).withStyle(Style.Error).append('some text')

        then:
        output.value == '{info}{error}some text{info}'
    }

    def appendsCharacterWithTemporaryStyleChange() {
        when:
        output.style(Style.Info).withStyle(Style.Error).append('c' as char)

        then:
        output.value == '{info}{error}c{info}'
    }

    def formatsTextWithTemporaryStyleChange() {
        when:
        output.style(Style.Info).withStyle(Style.Error).format('[%s]', 'message')

        then:
        output.value == '{info}{error}[message]{info}'
    }
}

class TestStyledTextOutput extends AbstractStyledTextOutput {
    StringBuilder result = new StringBuilder()

    @Override
    String toString() {
        result.toString()
    }

    def TestStyledTextOutput ignoreStyle() {
        return new TestStyledTextOutput() {
            @Override protected void doStyleChange(Style style) {
            }
        }
    }

    def String getRawValue() {
        return result.toString()
    }

    /**
     * Returns the normalised value of this text output. Normalises:
     * - style changes to {style} where _style_ is the lowercase name of the style.
     * - line endings to \n
     * - stack traces to {stacktrace}\n
     */
    def String getValue() {
        StringBuilder normalised = new StringBuilder()

        String eol = SystemProperties.lineSeparator
        boolean inStackTrace = false
        new StringTokenizer(result.toString().replaceAll(eol, '\n'), '\n', true).each { String line ->
            if (line == '\n') {
                if (!inStackTrace) {
                    normalised.append('\n')
                }
            } else if (line.matches(/\s+at .+\(.+\)/)) {
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

    @Override protected void doStyleChange(Style style) {
        result.append("{${style.toString().toLowerCase()}}")
    }

    @Override
    protected void doAppend(String text) {
        result.append(text)
    }
}
