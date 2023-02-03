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
import org.gradle.api.specs.Spec
import spock.lang.Specification

import static org.gradle.internal.Actions.composite
import static org.gradle.internal.Actions.doNothing
import static org.gradle.internal.Actions.filter
import static org.gradle.internal.Actions.toAction

class ActionsTest extends Specification {

    def "do nothing indeed does nothing"() {
        given:
        def thing = Mock(Object)

        when:
        doNothing().execute(thing)

        then:
        0 * thing._(*_)
    }

    def "can do nothing on null"() {
        when:
        doNothing().execute(null)

        then:
        notThrown(Throwable)
    }

    def "composite actions"() {
        def actions = [Mock(Action), Mock(Action)]

        when:
        composite(actions).execute("foo")

        then:
        actions.each { 1 * it.execute("foo") }
        0 * _._
    }

    def "composite action equality"() {
        given:
        def actions = (1..3).collect { Mock(Action) }

        expect:
        composite(actions[0], actions[1]) == composite(actions[0], actions[1])
        composite(actions[0], actions[1]) != composite(actions[1], actions[0])
        composite(actions[0], actions[1]) != composite(actions[0], actions[2])
        composite() == composite()
        composite() != composite(actions[0])
        composite(actions[0]) != composite()
    }

    def "adapting runnables"() {
        given:
        def runnable = Mock(Runnable)
        def arg = Mock(Object)

        when:
        toAction(runnable).execute(arg)

        then:
        0 * arg._(*_)
        1 * runnable.run()
    }

    def "adapting null runnables"() {
        when:
        toAction((Runnable) null).execute("foo")

        then:
        notThrown(Exception)
    }

    def "filtered action fires for matching"() {
        given:
        def called = false
        def action = filter(action { called = true }, spec { true })

        when:
        action.execute "object"

        then:
        called
    }

    def "filtered action doesnt fire for not matching"() {
        given:
        def called = false
        def action = filter(action { called = true }, spec { false })

        when:
        action.execute "object"

        then:
        !called
    }

    def "set of different actions"() {
        given:
        def called = []
        Action<?> a = Mock() {
            execute(_) >> { args -> args[0] << 'a' }
        }
        Action<?> b = Mock() {
            execute(_) >> { args -> args[0] << 'b' }
        }

        when:
        def set = Actions.set(a, b)
        set.execute(called)

        then:
        called == ['a', 'b']
    }

    def "set of different actions is preserving order"() {
        given:
        def called = []
        Action<?> a = Mock() {
            execute(_) >> { args -> args[0] << 'a' }
        }
        Action<?> b = Mock() {
            execute(_) >> { args -> args[0] << 'b' }
        }

        when:
        def set = Actions.set(b, a)
        set.execute(called)

        then:
        called == ['b', 'a']
    }

    def "deduplicates entries"() {
        given:
        def called = []
        Action<?> a = Mock() {
            execute(_) >> { args -> args[0] << 'a' }
        }
        Action<?> b = Mock() {
            execute(_) >> { args -> args[0] << 'b' }
        }
        Action<?> c = Mock() {
            execute(_) >> { args -> args[0] << 'c' }
        }

        when:
        def set = Actions.set(b, a)
        set = Actions.set(set, c)
        set = Actions.set(set, a)
        set.execute(called)

        then:
        called == ['b', 'a', 'c']
    }

    def "deduplicates entries using single call"() {
        given:
        def called = []
        Action<?> a = Mock() {
            execute(_) >> { args -> args[0] << 'a' }
        }
        Action<?> b = Mock() {
            execute(_) >> { args -> args[0] << 'b' }
        }
        Action<?> c = Mock() {
            execute(_) >> { args -> args[0] << 'c' }
        }

        when:
        def set = Actions.set(b, a, c, a, b)
        set.execute(called)

        then:
        called == ['b', 'a', 'c']
    }

    def "doesn't grow when adding a doNothing"() {
        when:
        def set = Actions.set(Actions.doNothing())

        then:
        set.empty
    }

    protected Spec spec(Closure spec) {
        spec as Spec
    }

    protected action(Closure action) {
        action as Action
    }

}
