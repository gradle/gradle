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

    def forwardsTextToListenerAtDefaultLevel() {
        when:
        output.text('message')

        then:
        1 * listener.onOutput({it.logLevel == LogLevel.INFO && it.category == 'category' && it.message == 'message'})
        0 * listener._
    }

    def forwardsTextToListenerWithSpecifiedLevel() {
        when:
        output.configure(LogLevel.ERROR)
        output.text('message')

        then:
        1 * listener.onOutput({it.logLevel == LogLevel.ERROR})
        0 * listener._
    }
}
