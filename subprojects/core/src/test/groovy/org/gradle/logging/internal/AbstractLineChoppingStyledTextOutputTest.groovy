/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.logging.StyledTextOutput
import org.gradle.util.SetSystemProperties
import org.gradle.internal.SystemProperties
import org.junit.Rule
import spock.lang.Specification

class AbstractLineChoppingStyledTextOutputTest extends Specification {
    @Rule final SetSystemProperties systemProperties = new SetSystemProperties()
    final StringBuilder result = new StringBuilder()
    final String eol = SystemProperties.instance.getLineSeparator()

    def "appends text to current line"() {
        def output = output()

        when:
        output.text("some text")

        then:
        result.toString() == "[some text]"
    }

    def "appends eol to current line"() {
        def output = output()

        when:
        output.text(eol)

        then:
        result.toString() == "[${eol}]{eol}{finish}"
    }

    def "append text that contains multiple lines"() {
        def output = output()

        when:
        output.text("a${eol}b")

        then:
        result.toString() == "[a${eol}]{eol}{finish}{start}[b]"
    }

    def "append text that ends with eol"() {
        def output = output()

        when:
        output.text("a${eol}")

        then:
        result.toString() == "[a${eol}]{eol}{finish}"

        when:
        output.text("b${eol}")
        output.text(eol)
        output.text("c")

        then:
        result.toString() == "[a${eol}]{eol}{finish}{start}[b${eol}]{eol}{finish}{start}[${eol}]{eol}{finish}{start}[c]"
    }

    def "can append eol in chunks"() {
        System.setProperty("line.separator", "----");
        def output = output()

        when:
        output.text("a--")
        
        then:
        result.toString() == "[a--]"
        
        when:
        output.text("--b")
        
        then:
        result.toString() == "[a--][--]{eol}{finish}{start}[b]"
    }

    def "can append eol prefix"() {
        System.setProperty("line.separator", "----");
        def output = output()

        when:
        output.text("--")
        
        then:
        result.toString() == "[--]"

        when:
        output.text("a")

        then:
        result.toString() == "[--][a]"
    }

    def "can split eol across style changes"() {
        System.setProperty("line.separator", "----");
        def output = output()

        when:
        output.text("--")
        output.style(StyledTextOutput.Style.Failure)
        output.text("--")

        then:
        result.toString() == "[--]{style}[--]{eol}{finish}"
    }

    def output() {
        final AbstractLineChoppingStyledTextOutput output = new AbstractLineChoppingStyledTextOutput() {
            @Override
            protected void doStyleChange(StyledTextOutput.Style style) {
                result.append("{style}")
            }

            @Override
            protected void doFinishLine() {
                result.append("{finish}")
            }

            @Override
            protected void doStartLine() {
                result.append("{start}")
            }

            @Override
            protected void doLineText(CharSequence text, boolean terminatesLine) {
                result.append("[")
                result.append(text)
                result.append("]")
                if (terminatesLine) {
                    result.append("{eol}")
                }
            }
        }
        return output
    }
}
