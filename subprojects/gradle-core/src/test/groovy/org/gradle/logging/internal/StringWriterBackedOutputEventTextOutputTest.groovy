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

import spock.lang.Specification

class StringWriterBackedOutputEventTextOutputTest extends Specification {
    private final StringWriterBackedOutputEventTextOutput output = new StringWriterBackedOutputEventTextOutput()

    def writesText() {
        when:
        output.text('some message')

        then:
        output.toString() == 'some message'
    }

    def onOutputWritesText() {
        when:
        output.onOutput('some message')

        then:
        output.toString() == 'some message'
    }
    
    def writesEndOfLine() {
        when:
        output.endLine()

        then:
        output.toString() == System.getProperty('line.separator')
    }

    def appendsCharacter() {
        when:
        output.append('c' as char)

        then:
        output.toString() == 'c'
    }

    def appendsCharSequence() {
        when:
        output.append('some message')

        then:
        output.toString() == 'some message'
    }

    def appendsNullCharSequence() {
        when:
        output.append(null)

        then:
        output.toString() == 'null'
    }

    def appendsCharSubsequence() {
        when:
        output.append('some message', 5, 9)

        then:
        output.toString() == 'mess'
    }
    
    def appendsNullCharSubsequence() {
        when:
        output.append(null, 5, 9)

        then:
        output.toString() == 'null'
    }

    def writesException() {
        when:
        output.exception(new RuntimeException('broken'))

        then:
        output.toString().startsWith('java.lang.RuntimeException: broken')
    }

}
