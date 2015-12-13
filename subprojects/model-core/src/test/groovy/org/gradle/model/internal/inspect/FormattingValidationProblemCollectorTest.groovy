/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.inspect

import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class FormattingValidationProblemCollectorTest extends Specification {
    def "formats message with a single problem"() {
        given:
        def collector = new FormattingValidationProblemCollector(ModelType.of(String))
        collector.add("does not extend RuleSource")

        expect:
        collector.format() == 'Type java.lang.String is not a valid rule source: does not extend RuleSource'
    }

    def "formats message with a single problem with a long message"() {
        given:
        def collector = new FormattingValidationProblemCollector(ModelType.of(String))
        collector.add("does not extend RuleSource and is not really that great, it could be much simpler")

        expect:
        collector.format() == '''Type java.lang.String is not a valid rule source:
- does not extend RuleSource and is not really that great, it could be much simpler'''
    }

    def "formats message with a single method problem"() {
        given:
        def collector = new FormattingValidationProblemCollector(ModelType.of(String))
        collector.add(String.class.getMethod("indexOf", String), "is not annotated with anything.")

        expect:
        collector.format() == '''Type java.lang.String is not a valid rule source:
- Method indexOf(java.lang.String) is not a valid rule method: is not annotated with anything.'''
    }

    def "formats message with multiple problems"() {
        given:
        def collector = new FormattingValidationProblemCollector(ModelType.of(String))
        collector.add("does not extend RuleSource")
        collector.add("does not have any rule method")

        expect:
        collector.format() == '''Type java.lang.String is not a valid rule source:
- does not extend RuleSource
- does not have any rule method'''
    }
}
