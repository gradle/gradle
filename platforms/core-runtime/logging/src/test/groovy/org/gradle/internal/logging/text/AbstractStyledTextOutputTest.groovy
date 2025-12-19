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

    def "onOutput writes text"() {
        when:
        output.onOutput('some message')

        then:
        output.value == 'some message'
    }

    def "writes null text"() {
        when:
        output.text(null)

        then:
        output.value == 'null'
    }

    def "writes end of line"() {
        when:
        output.println()

        then:
        output.rawValue == SystemProperties.instance.lineSeparator
    }

    def "appends character"() {
        when:
        output.append('c' as char)

        then:
        output.value == 'c'
    }

    def "appends char sequence"() {
        when:
        output.append('some message')

        then:
        output.value == 'some message'
    }

    def "appends null char sequence"() {
        when:
        output.append(null)

        then:
        output.value == 'null'
    }

    def "appends char subsequence"() {
        when:
        output.append('some message', 5, 9)

        then:
        output.value == 'mess'
    }

    def "appends null char subsequence"() {
        when:
        output.append(null, 5, 9)

        then:
        output.value == 'null'
    }

    def "println writes text and end of line"() {
        when:
        output.println('message')

        then:
        output.value == 'message\n'
    }

    def "formats text"() {
        when:
        output.format('[%s]', 'message')

        then:
        output.value == '[message]'
    }

    def "formats text and end of line"() {
        when:
        output.formatln('[%s]', 'message')

        then:
        output.value == '[message]\n'
    }

    def "formats exception"() {
        when:
        output.exception(new RuntimeException('broken'))

        then:
        output.value == 'java.lang.RuntimeException: broken\n{stacktrace}\n'
    }

    def "can MixIn style information"() {
        when:
        output.style(Style.Info).text('info test').style(Style.Normal)

        then:
        output.value == '{info}info test{normal}'
    }

    def "ignores style change when already using the given style"() {
        when:
        output.style(Style.Info).text('info test').style(Style.Info)

        then:
        output.value == '{info}info test'
    }

    def "writes text with temporary style change"() {
        when:
        output.style(Style.Info).withStyle(Style.Error).text('some text')

        then:
        output.value == '{info}{error}some text{info}'
    }

    def "writes text and end of line with temporary style change"() {
        when:
        output.style(Style.Info).withStyle(Style.Error).println('some text')

        then:
        output.value == '{info}{error}some text{info}\n'
    }

    def "appends text with temporary style change"() {
        when:
        output.style(Style.Info).withStyle(Style.Error).append('some text')

        then:
        output.value == '{info}{error}some text{info}'
    }

    def "appends character with temporary style change"() {
        when:
        output.style(Style.Info).withStyle(Style.Error).append('c' as char)

        then:
        output.value == '{info}{error}c{info}'
    }

    def "formats text with temporary style change"() {
        when:
        output.style(Style.Info).withStyle(Style.Error).format('[%s]', 'message')

        then:
        output.value == '{info}{error}[message]{info}'
    }
}

