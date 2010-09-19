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
        output.value == System.getProperty('line.separator')
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
        output.value == toNative('message\n')
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
        output.value == toNative('[message]\n')
    }
}

class TestStyledTextOutput extends AbstractStyledTextOutput {
    StringBuilder result = new StringBuilder()

    @Override
    String toString() {
        result.toString()
    }

    def getValue() {
        result.toString()
    }

    @Override
    protected void doAppend(String text) {
        result.append(text)
    }
}
