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
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class LinePrefixingStyledTextOutputTest extends Specification {

    StringBuilder result

    StyledTextOutput styledTextOutput

    def setup() {
        result = new StringBuilder();
        styledTextOutput = output()
    }

    def "adds prefix to every line"() {
        LinePrefixingStyledTextOutput output = new LinePrefixingStyledTextOutput(styledTextOutput, "[PREFIX]")

        when:
        output.println("1st line")
        output.text("2nd line")
        output.text(" - still 2nd line")
        output.println()
        output.text("3rd line")

        then:
        result.toString() == TextUtil.toPlatformLineSeparators("""[PREFIX]1st line
[PREFIX]2nd line - still 2nd line
[PREFIX]3rd line""")
    }

    def "allows not prefixing first line"() {
        LinePrefixingStyledTextOutput output = new LinePrefixingStyledTextOutput(styledTextOutput, "[PREFIX]", false)

        when:
        output.println("1st line")
        output.text("2nd line")
        output.text(" - still 2nd line")
        output.println()
        output.text("3rd line")

        then:
        result.toString() == TextUtil.toPlatformLineSeparators("""1st line
[PREFIX]2nd line - still 2nd line
[PREFIX]3rd line""")
    }


    StyledTextOutput output() {
        return new StyledTextOutput() {
            @Override
            StyledTextOutput append(char c) {
                result.append(c)
                return this
            }

            @Override
            StyledTextOutput append(CharSequence csq) {
                result.append(csq)
                return this
            }

            @Override
            StyledTextOutput append(CharSequence csq, int start, int end) {
                result.append(csq, start, end)
                return null
            }

            @Override
            StyledTextOutput style(StyledTextOutput.Style style) {
                return null
            }

            @Override
            StyledTextOutput withStyle(StyledTextOutput.Style style) {
                return null
            }

            @Override
            StyledTextOutput text(Object text) {
                result.append(text.toString());
                return this
            }

            @Override
            StyledTextOutput println(Object text) {
                result.append(text.toString()).append(SystemProperties.instance.lineSeparator)
                return this
            }

            @Override
            StyledTextOutput format(String pattern, Object... args) {
                return null
            }

            @Override
            StyledTextOutput formatln(String pattern, Object... args) {
                return null
            }

            @Override
            StyledTextOutput println() {
                result.append(SystemProperties.instance.lineSeparator)
                return this
            }

            @Override
            StyledTextOutput exception(Throwable throwable) {
                return null
            }
        }

    }


}
