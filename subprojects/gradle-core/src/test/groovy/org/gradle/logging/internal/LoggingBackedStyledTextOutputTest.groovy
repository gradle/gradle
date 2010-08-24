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

import org.gradle.api.logging.LogLevel
import spock.lang.Specification

class LoggingBackedStyledTextOutputTest extends Specification {
    private final OutputEventListener listener = Mock()
    private final LoggingBackedStyledTextOutput output = new LoggingBackedStyledTextOutput(listener, 'category', LogLevel.INFO)

    def forwardsLineOfTextToListenerAtDefaultLevel() {
        when:
        output.text('message').endLine()

        then:
        1 * listener.onOutput({it.logLevel == LogLevel.INFO && it.category == 'category' && it.message == toNative('message\n')})
        0 * listener._
    }

    def forwardsEachLineOfTextToListener() {
        when:
        output.text(toNative('message1\nmessage2')).endLine()

        then:
        1 * listener.onOutput({it.message == toNative('message1\n')})
        1 * listener.onOutput({it.message == toNative('message2\n')})
        0 * listener._
    }

    def forwardsEmptyLinesToListener() {
        when:
        output.text(toNative('\n\n'))

        then:
        2 * listener.onOutput({it.message == toNative('\n')})
        0 * listener._
    }

    def forwardsBufferedTextToListenerOnFlush() {
        when:
        output.text('message1')
        output.flush()

        then:
        1 * listener.onOutput({it.message == 'message1'})
        0 * listener._
    }
    
    def forwardsTextToListenerWithSpecifiedLevel() {
        when:
        output.configure(LogLevel.ERROR)
        output.text('message').endLine()

        then:
        1 * listener.onOutput({it.logLevel == LogLevel.ERROR})
        0 * listener._
    }

    private String toNative(String value) {
        return value.replaceAll('\n', System.getProperty('line.separator'))
    }
}
