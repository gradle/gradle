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
import spock.lang.Issue
import spock.lang.Specification

class AbstractLineChoppingStyledTextOutputTest extends Specification {
    private static final String NIX_EOL = "\n"
    private static final String WINDOWS_EOL = "\r\n"
    private static final String SYSTEM_EOL = SystemProperties.instance.getLineSeparator()
    private static final List<List<String>> EOLS = [
        ["System", SYSTEM_EOL],
        ["*nix", NIX_EOL],
        ["Windows", WINDOWS_EOL]
    ]
    final StringBuilder result = new StringBuilder()

    def "appends text to current line"() {
        def output = output()

        when:
        output.text("some text")

        then:
        result.toString() == "[some text]"
    }

    def "append empty lines [#type]"() {
        def output = output()

        when:
        output.text(eol)
        output.text(eol)
        output.text("$eol$eol")

        then:
        result.toString() == "{eol}{start}{eol}{start}{eol}{start}{eol}"

        where:
        [type, eol] << EOLS
    }

    def "appends eol to current line [#type]"() {
        def output = output()

        when:
        output.text("some text")
        output.text(eol)

        then:
        result.toString() == "[some text]{eol}"

        where:
        [type, eol] << EOLS
    }

    def "append text that contains multiple lines [#type]"() {
        def output = output()

        when:
        output.text("a${eol}b")

        then:
        result.toString() == "[a]{eol}{start}[b]"

        where:
        [type, eol] << EOLS
    }

    def "append text that ends with eol [#type]"() {
        def output = output()

        when:
        output.text("a${eol}")

        then:
        result.toString() == "[a]{eol}"

        when:
        output.text("b${eol}")
        output.text(eol)
        output.text("c")

        then:
        result.toString() == "[a]{eol}{start}[b]{eol}{start}{eol}{start}[c]"

        where:
        [type, eol] << EOLS
    }

    def "can append eol in chunks"() {
        def output = output("----")

        when:
        output.text("a--")

        then:
        result.toString() == "[a]"

        when:
        output.text("--b")

        then:
        result.toString() == "[a]{eol}{start}[b]"
    }

    def "can append eol prefix"() {
        def output = output("----")

        when:
        output.text("a--")

        then:
        result.toString() == "[a]"

        when:
        output.text("-a-")
        output.text("-")
        output.text("-a")

        then:
        result.toString() == "[a][---][a][---][a]"
    }

    @Issue("https://github.com/gradle/gradle/issues/2077")
    def "can append consecutive return character on Windows"() {
        def output = output("\r\n")

        when:
        output.text('\r')
        output.text("\r\na")

        then:
        result.toString() == "[\r]{eol}{start}[a]"
    }

    def "can append data after a carriage return on Windows"() {
        def output = output("\r\n")

        when:
        output.text('\r')
        output.text('a')

        then:
        result.toString() == "[\ra]"
    }

    def "can append new line after multiple carriage return followed by data on Windows"() {
        def output = output("\r\n")

        when:
        output.text('\r\r\r')
        output.text('\r\r\r\na')

        then:
        result.toString() == "[\r\r][\r\r\r]{eol}{start}[a]"
    }

    def "can split eol across style changes"() {
        def output = output("----")

        when:
        output.text("--")
        output.style(StyledTextOutput.Style.Failure)
        output.text("--")

        then:
        result.toString() == "{style}{eol}"
    }

    def "can split mixed eol"() {
        def output = output()

        when:
        output.text(SYSTEM_EOL)
        output.text("$WINDOWS_EOL$NIX_EOL")

        then:
        result.toString() == "{eol}{start}{eol}{start}{eol}"
    }

    def "can split Windows eol across multiple call on non-Windows eol default"() {
        def output = output("\n")

        when:
        output.text("\r")
        output.text("\n")

        then:
        result.toString() == "{eol}"
    }

    def "Carriage return isn't detected as new line [#type]"() {
        def output = output(eol as String)

        when:
        output.text("1\r2\r3\r")

        then:
        result.toString() == "[1\r2\r3]"

        when:
        output.text("4\r5\r6\r")

        then:
        result.toString() == "[1\r2\r3][\r4\r5\r6]"

        where:
        [type, eol] << EOLS
    }

    def output(String eol = SystemProperties.getInstance().lineSeparator) {
        final AbstractLineChoppingStyledTextOutput output = new AbstractLineChoppingStyledTextOutput(eol) {
            @Override
            protected void doStyleChange(StyledTextOutput.Style style) {
                result.append("{style}")
            }

            @Override
            protected void doStartLine() {
                result.append("{start}")
            }

            @Override
            protected void doLineText(CharSequence text) {
                result.append("[")
                result.append(text)
                result.append("]")
            }

            @Override
            protected void doEndLine(CharSequence endOfLine) {
                assert String.valueOf(endOfLine) in [eol, NIX_EOL, WINDOWS_EOL]
                result.append("{eol}")
            }
        }
        return output
    }
}
