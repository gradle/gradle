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

package org.gradle.api.reporting.components.internal
import org.gradle.api.Named
import spock.lang.Specification

class RendererUtilsTest extends Specification {
    def "returns '#expected' for simple value #value"() {
        expect:
        RendererUtils.displayValueOf(value) == expected

        where:
        value | expected
        null  | "null"
        1     | "1"
        true  | "true"
    }

    def "returns '#expected' for #value.getClass().simpleName"() {

        when:
        def result = RendererUtils.displayValueOf(value)

        then:
        result == expected

        where:
        value                                | expected
        new SimpleNamed(name: null)          | "null"
        new SimpleNamed(name: "Lajos")       | "Lajos"
        new NamedWithToString(name: null)    | "toString():null"
        new NamedWithToString(name: "Lajos") | "toString():Lajos"
    }

    def "returns '#expected' when toString() returns #toStringReturns"() {
        def value = Mock(Object)

        when:
        def result = RendererUtils.displayValueOf(value)

        then:
        result == expected
        _ * value.toString() >> toStringReturns

        where:
        toStringReturns     | expected
        null                | "null"
        "toString() called" | "toString() called"
    }

    def "toString() declared in super-class takes precedence over Named"() {
        def value = new NamedExtendingHasToString(name: "name", superToString: "super-to-string")

        expect:
        RendererUtils.displayValueOf(value) == "super-to-string"
    }

    static class SimpleNamed implements Named {
        String name
    }

    static class NamedWithToString extends SimpleNamed {
        @Override
        String toString() {
            return "toString():" + name
        }
    }

    static class HasToString {
        String superToString
        @Override
        String toString() {
            return superToString
        }
    }

    static class NamedExtendingHasToString extends HasToString implements Named {
        String name
    }
}
