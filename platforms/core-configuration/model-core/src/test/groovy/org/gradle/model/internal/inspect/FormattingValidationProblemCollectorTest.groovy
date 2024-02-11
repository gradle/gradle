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

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class FormattingValidationProblemCollectorTest extends Specification {
    def "formats message with a single problem"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(String))
        collector.add("does not extend RuleSource")

        expect:
        collector.format() == 'Type java.lang.String is not a valid <thing>: does not extend RuleSource'
    }

    def "formats message with a single problem with a long message"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(String))
        collector.add("does not extend RuleSource and is not really that great, it could be much simpler")

        expect:
        collector.format() == '''Type java.lang.String is not a valid <thing>:
- does not extend RuleSource and is not really that great, it could be much simpler'''
    }

    def "formats message with a single method problem"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(String))
        collector.add(String.class.getMethod("indexOf", String), "rule", "is not annotated with anything.")

        expect:
        collector.format() == '''Type java.lang.String is not a valid <thing>:
- Method indexOf(java.lang.String) is not a valid rule method: is not annotated with anything.'''
    }

    def "formats message with a problem with inherited method"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(WithConstructor))
        collector.add(SuperClass.class.getMethod("thing"), "rule", "is not annotated with anything.")

        expect:
        collector.format() == """Type ${fullyQualifiedNameOf(WithConstructor)} is not a valid <thing>:
- Method FormattingValidationProblemCollectorTest.SuperClass.thing() is not a valid rule method: is not annotated with anything."""
    }

    def "formats message with multiple problems"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(String))
        collector.add("does not extend RuleSource")
        collector.add("does not have any rule method")

        expect:
        collector.format() == '''Type java.lang.String is not a valid <thing>:
- does not extend RuleSource
- does not have any rule method'''
    }

    static class SuperClass {
        private String value
        void thing() { }
    }

    static class WithConstructor extends SuperClass {
        String value
        WithConstructor(String s) {}
    }

    def "formats message with constructor problems"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(WithConstructor))
        collector.add(WithConstructor.getDeclaredConstructor(String), "doesn't do anything")
        collector.add(WithConstructor.getDeclaredConstructor(String), "should accept an int")

        expect:
        collector.format() == """Type ${fullyQualifiedNameOf(WithConstructor)} is not a valid <thing>:
- Constructor FormattingValidationProblemCollectorTest.WithConstructor(java.lang.String) is not valid: doesn't do anything
- Constructor FormattingValidationProblemCollectorTest.WithConstructor(java.lang.String) is not valid: should accept an int"""
    }

    def "formats message with field problems"() {
        given:
        def collector = new FormattingValidationProblemCollector("<thing>", ModelType.of(WithConstructor))
        collector.add(WithConstructor.getDeclaredField("value"), "should have an initializer")
        collector.add(WithConstructor.getDeclaredField("value"), "should accept an int")
        collector.add(SuperClass.getDeclaredField("value"), "cannot have fields")

        expect:
        collector.format() == """Type ${fullyQualifiedNameOf(WithConstructor)} is not a valid <thing>:
- Field value is not valid: should have an initializer
- Field value is not valid: should accept an int
- Field FormattingValidationProblemCollectorTest.SuperClass.value is not valid: cannot have fields"""
    }
}
