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
package org.gradle.internal.logging.services

import org.gradle.internal.logging.text.StreamBackedStandardOutputListener
import spock.lang.Specification

class StreamBackedStandardOutputListenerTest extends Specification {
    def canAppendToAnAppendable() {
        Appendable appendable = Mock()
        def listener = new StreamBackedStandardOutputListener(appendable)

        when:
        listener.onOutput("text")

        then:
        1 * appendable.append("text")
        0 * _._
    }

    def canAppendToAWriter() {
        Writer writer = Mock()
        def listener = new StreamBackedStandardOutputListener(writer)

        when:
        listener.onOutput("text")

        then:
        1 * writer.append("text")
        1 * writer.flush()
        0 * _._
    }
    
    def canAppendToAnOutputStream() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        def listener = new StreamBackedStandardOutputListener(outputStream)

        when:
        listener.onOutput("text")

        then:
        outputStream.toString() == "text"
    }
}
