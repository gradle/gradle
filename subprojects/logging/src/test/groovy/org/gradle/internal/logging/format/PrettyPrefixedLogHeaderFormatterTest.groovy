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

import org.gradle.internal.logging.text.StyledTextOutput
import spock.lang.Specification

class PrettyPrefixedLogHeaderFormatterTest extends Specification {

    def "prints header of failing tasks red"() {
        setup:
        def formatter = new PrettyPrefixedLogHeaderFormatter()

        when:
        def formattedText = formatter.format(":test", "", null, "XYZ", true)

        then:
        formattedText[1].style == StyledTextOutput.Style.FailureHeader
    }

    def "prints header of not-failing tasks white"() {
        setup:
        def formatter = new PrettyPrefixedLogHeaderFormatter()

        when:
        def formattedText = formatter.format(":test", "", null, "XYZ", false)

        then:
        formattedText[1].style == StyledTextOutput.Style.Header
    }
}
