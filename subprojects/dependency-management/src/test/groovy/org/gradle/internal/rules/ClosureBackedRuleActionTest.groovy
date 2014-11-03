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

package org.gradle.internal.rules

import org.gradle.util.Matchers
import spock.lang.Specification

class ClosureBackedRuleActionTest extends Specification {

    def "one arg closure called with subject and no inputs"() {
        given:
        def called = false
        String thing = "1"
        def closure = { String val ->
            called = true
            assert val.is(thing)
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing, [])

        then:
        called
    }

    def "multiple arg closure called with correct subject and correct inputs"() {
        given:
        def called = false
        String thing = "1"
        def closure = { String subject, Integer other, String another ->
            called = true
            assert subject.is(thing)
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing, [12, "another"])

        then:
        called
    }

    def "can construct with zero arg closure"() {
        given:
        def called = false
        String thing = "1"
        def closure = { ->
            called = true
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing, [])

        then:
        called
    }

    def "can construct with empty arg closure"() {
        given:
        def called = false
        String thing = "1"
        def closure = {
            called = true
            assert it.is(thing)
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing, [])

        then:
        called
    }

    def "can construct with untyped single arg closure"() {
        given:
        def called = false
        String thing = "1"
        def closure = { subject ->
            called = true
            assert subject.is(thing)
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing, [])

        then:
        called
    }

    def "fails to construct with incorrect subject type"() {
        given:
        def closure = { Integer val ->
        }

        when:
        action(closure)

        then:
        def e = thrown RuleActionValidationException
        e.message == "First parameter of rule action closure must be of type 'String'."
    }

    def "fails to construct with multiple arg closure with incorrect subject"() {
        given:
        def closure = { Integer val, String other ->
        }

        when:
        action(closure)

        then:
        def e = thrown RuleActionValidationException
        e.message == "First parameter of rule action closure must be of type 'String'."
    }

    def "equality"() {
        given:
        def c = {String foo -> }
        def a1 = action(c)

        expect:
        Matchers.strictlyEquals(a1, action(c))
        a1 == action(c)
        a1 != action({String bar -> })
        a1 != new ClosureBackedRuleAction(Integer.class, { Integer val -> })
    }

    RuleAction<String> action(Closure<?> c) {
        new ClosureBackedRuleAction<String>(String.class, c)
    }
}
