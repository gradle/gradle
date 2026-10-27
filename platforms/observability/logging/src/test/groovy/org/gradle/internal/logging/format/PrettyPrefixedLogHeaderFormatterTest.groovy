/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.format

import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.StyledTextOutput
import spock.lang.Specification

class PrettyPrefixedLogHeaderFormatterTest extends Specification {

    def "formats header of failed operation that has status"() {
        setup:
        def formatter = new PrettyPrefixedLogHeaderFormatter()

        when:
        def formattedText = formatter.format(":test", "XYZ", true)

        then:
        formattedText.size() == 3
        formattedText[0].style == StyledTextOutput.Style.FailureHeader
        formattedText[0].text == "> :test"
        formattedText[1].style == StyledTextOutput.Style.Failure
        formattedText[1].text == " XYZ"
        formattedText[2] == StyledTextOutputEvent.EOL
    }

    def "formats header of failed operation that has empty status"() {
        setup:
        def formatter = new PrettyPrefixedLogHeaderFormatter()

        when:
        def formattedText = formatter.format(":test", "", true)

        then:
        formattedText.size() == 2
        formattedText[0].style == StyledTextOutput.Style.FailureHeader
        formattedText[0].text == "> :test"
        formattedText[1] == StyledTextOutputEvent.EOL
    }

    def "formats header of successful operation that has status"() {
        setup:
        def formatter = new PrettyPrefixedLogHeaderFormatter()

        when:
        def formattedText = formatter.format(":test", "XYZ", false)

        then:
        formattedText.size() == 3
        formattedText[0].style == StyledTextOutput.Style.Header
        formattedText[0].text == "> :test"
        formattedText[1].style == StyledTextOutput.Style.Info
        formattedText[1].text == " XYZ"
        formattedText[2] == StyledTextOutputEvent.EOL
    }

    def "formats header of successful operation that has empty status"() {
        setup:
        def formatter = new PrettyPrefixedLogHeaderFormatter()

        when:
        def formattedText = formatter.format(":test", "", false)

        then:
        formattedText.size() == 2
        formattedText[0].style == StyledTextOutput.Style.Header
        formattedText[0].text == "> :test"
        formattedText[1] == StyledTextOutputEvent.EOL
    }
}
