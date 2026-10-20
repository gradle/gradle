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
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.text.StyledTextOutput.Style

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
        output.rawValue == SystemProperties.instance.lineSeparator
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

