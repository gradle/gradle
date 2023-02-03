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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.InvalidActionClosureException
import org.gradle.util.internal.ClosureBackedAction
import spock.lang.Specification

class ClosureBackedActionTest extends Specification {

    def "one arg closure is called"() {
        given:
        def called = false
        def thing = "1"
        def closure = {
            called = true
            assert it.is(thing)
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing)

        then:
        called
    }

    def "zero arg closure is called"() {
        given:
        def called = false
        def thing = "1"
        def closure = { ->
            called = true
            assert delegate.is(thing)
        }

        when:
        action(closure).execute(thing)

        then:
        called
    }

    def "closure with wrong param type is given"() {
        given:
        def closure = { Map m -> }
        def arg = "1"

        when:
        action(closure).execute(arg)

        then:
        def e = thrown InvalidActionClosureException
        e.closure.is(closure)
        e.message == "The closure '${closure.toString()}' is not valid as an action for argument '1'. It should accept no parameters, or one compatible with type 'java.lang.String'. It accepts (java.util.Map)."
    }

    def "closure with more than one param type is given"() {
        given:
        def closure = { Map m, List l -> }
        def arg = "1"

        when:
        action(closure).execute(arg)

        then:
        def e = thrown InvalidActionClosureException
        e.closure.is(closure)
        e.message == "The closure '${closure.toString()}' is not valid as an action for argument '1'. It should accept no parameters, or one compatible with type 'java.lang.String'. It accepts (java.util.Map, java.util.List)."
    }

    def "equality"() {
        given:
        def c = {}
        def a1 = action(c)

        expect:
        a1 == action(c)
        a1 != new ClosureBackedAction(c, Closure.OWNER_ONLY)
        a1 != new ClosureBackedAction(c, Closure.DELEGATE_FIRST, false)
        a1 != action({})
    }

    Action<?> action(Closure<?> c) {
        new ClosureBackedAction(c)
    }
}
