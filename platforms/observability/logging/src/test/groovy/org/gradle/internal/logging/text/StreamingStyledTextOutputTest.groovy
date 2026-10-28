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

import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.text.StyledTextOutput.Style

class StreamingStyledTextOutputTest extends OutputSpecification {
    def forwardsTextToAListener() {
        StandardOutputListener listener = Mock()
        StreamingStyledTextOutput output = new StreamingStyledTextOutput(listener)

        when:
        output.text('text')

        then:
        1 * listener.onOutput('text')
    }

    def forwardsTextToAnAppendable() {
        Appendable appendable = Mock()
        StreamingStyledTextOutput output = new StreamingStyledTextOutput(appendable)

        when:
        output.text('text')

        then:
        1 * appendable.append('text')
    }

    def ignoresStyleInformation() {
        StandardOutputListener listener = Mock()
        StreamingStyledTextOutput output = new StreamingStyledTextOutput(listener)

        when:
        output.style(Style.Error).text('text').style(Style.Normal)

        then:
        1 * listener.onOutput('text')
    }

    def closeDoesNothingWhenForwardingToANonCloseable() {
        Appendable appendable = Mock()
        StreamingStyledTextOutput output = new StreamingStyledTextOutput(appendable)

        when:
        output.close()

        then:
        0 * appendable._
    }
    
    def closeClosesTargetWhenItImplementsCloseable() {
        Writer writer = Mock()
        StreamingStyledTextOutput output = new StreamingStyledTextOutput(writer)

        when:
        output.close()

        then:
        1 * writer.close()
    }

}
