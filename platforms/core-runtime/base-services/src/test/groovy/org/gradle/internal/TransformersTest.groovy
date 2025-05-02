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

package org.gradle.internal

import org.gradle.api.Action
import spock.lang.Specification

import static org.gradle.internal.Transformers.*

class TransformersTest extends Specification {

    def "casting"() {
        given:
        def arg = "1"

        when:
        cast(Integer).transform(arg)

        then:
        def e = thrown(ClassCastException)
        e.message == "Failed to cast object $arg of type ${arg.class.name} to target type ${Integer.name}"

        when:
        def result = cast(CharSequence).transform(arg)

        then:
        notThrown(ClassCastException)
        result.is arg
    }

    def "as string"() {
        expect:
        asString().transform(1) == "1"
        asString().transform(null) == null
    }

    def "to URL"() {
        expect:
        toURL().transform(new URI("http://localhost:80/path")) == new URL("http://localhost:80/path")
    }

    def "by type"() {
        expect:
        type().transform("foo") == String
    }

    def "action as transformer"() {
        def action = Mock(Action)

        when:
        def result = toTransformer(action).transform("original")

        then:
        1 * action.execute("original")

        and:
        result == null
    }

    def "factory as transformer"() {
        def factory = Mock(Factory)

        when:
        def result = toTransformer(factory).transform("original")

        then:
        1 * factory.create() >> "transformed"
        result == "transformed"
    }

    def constants() {
        def foo = "foo"
        expect:
        constant(foo).transform(null).is(foo)
    }
}
