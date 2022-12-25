/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.typeconversion

import spock.lang.Specification

class CharSequenceNotationConverterTest extends Specification {
    def target = Mock(NotationConverter)
    def result = Mock(NotationConvertResult)
    def converter = new CharSequenceNotationConverter<Object, Number>(target)

    def "ignores notations that are not a CharSequence instance"() {
        when:
        converter.convert(12, result)

        then:
        0 * _._
    }

    def "delegates to target converter when notation is an instance of #type"() {
        when:
        converter.convert(value, result)

        then:
        1 * target.convert("12", result)

        where:
        type          | value
        String        | "12"
        StringBuilder | new StringBuilder("12")
        GString       | "${6 * 2}"
    }
}
