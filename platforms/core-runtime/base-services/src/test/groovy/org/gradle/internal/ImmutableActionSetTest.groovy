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

package org.gradle.internal

import org.gradle.api.Action
import spock.lang.Specification

class ImmutableActionSetTest extends Specification {
    def "creates an empty set that does nothing"() {
        expect:
        def set = ImmutableActionSet.empty()
        set.execute("value")
        set.empty
    }

    def "can add no-op action to empty set"() {
        expect:
        def set = ImmutableActionSet.empty().add(Actions.doNothing())
        set.is(ImmutableActionSet.empty())
    }

    def "can add empty set to empty set"() {
        expect:
        def set = ImmutableActionSet.empty().add(ImmutableActionSet.empty())
        set.is(ImmutableActionSet.empty())
    }

    def "can create singleton set"() {
        def action = Mock(Action)

        when:
        def set = ImmutableActionSet.empty().add(action)

        then:
        !set.empty

        when:
        set.execute("value")

        then:
        1 * action.execute("value")
        0 * _
    }

    def "can add no-op action to singleton set"() {
        def action = Mock(Action)

        expect:
        def set = ImmutableActionSet.empty().add(action)
        set.add(Actions.doNothing()).is(set)
    }

    def "can add empty set to singleton set"() {
        def action = Mock(Action)

        expect:
        def set = ImmutableActionSet.empty().add(action)
        set.add(ImmutableActionSet.empty()).is(set)
    }

    def "can add duplicate action to singleton set"() {
        def action = Mock(Action)

        expect:
        def set = ImmutableActionSet.empty().add(action)
        set.add(action).is(set)
        set.add(ImmutableActionSet.of(action)).is(set)
    }

    def "can add action to singleton set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        when:
        def set = ImmutableActionSet.empty().add(action1).add(action2)

        then:
        !set.empty

        when:
        set.execute("value")

        then:
        1 * action1.execute("value")
        1 * action2.execute("value")
        0 * _
    }

    def "can create a set from multiple actions"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def action4 = Mock(Action)
        def action5 = Mock(Action)
        def action6 = Mock(Action)

        when:
        def setWith6 = ImmutableActionSet.of(action1, action2, action3, action4, action5, action6)

        then:
        !setWith6.empty

        when:
        setWith6.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")

        then:
        1 * action6.execute("value")
        0 * _

        when:
        def setWith5 = ImmutableActionSet.of(action1, action2, action3, action4, action5)

        then:
        !setWith5.empty

        when:
        setWith5.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")
        0 * _

        when:
        def setWith4 = ImmutableActionSet.of(action1, action2, action3, action4)

        then:
        !setWith4.empty

        when:
        setWith4.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")
        0 * _

        when:
        def setWith3 = ImmutableActionSet.of(action1, action2, action3)

        then:
        !setWith3.empty

        when:
        setWith3.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")
        0 * _

        when:
        def setWith2 = ImmutableActionSet.of(action1, action2)

        then:
        !setWith2.empty

        when:
        setWith2.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")
        0 * _

        when:
        def setWith1 = ImmutableActionSet.of(action1)

        then:
        !setWith1.empty

        when:
        setWith1.execute("value")

        then:
        1 * action1.execute("value")
        0 * _

        when:
        def setWith0 = ImmutableActionSet.of()

        then:
        setWith0.is(ImmutableActionSet.empty())
    }

    def "can add multiple actions to a set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def action4 = Mock(Action)
        def action5 = Mock(Action)
        def action6 = Mock(Action)
        def action7 = Mock(Action)

        when:
        def set = ImmutableActionSet.empty().add(action1)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")
        0 * _

        when:
        set = set.add(action2)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")
        0 * _

        when:
        set = set.add(action3)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")
        0 * _

        when:
        set = set.add(action4)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")
        0 * _

        when:
        set = set.add(action5)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")
        0 * _

        when:
        set = set.add(action6)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")

        then:
        1 * action6.execute("value")
        0 * _

        when:
        set = set.add(action7)
        set.execute("value")

        then:
        !set.empty
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")

        then:
        1 * action6.execute("value")

        then:
        1 * action7.execute("value")
        0 * _
    }

    def "execution stops on first failure"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def failure = new RuntimeException()

        when:
        def set = ImmutableActionSet.of(action1, action2, action3)
        set.execute("value")

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * action1.execute("value")
        1 * action2.execute("value") >> { throw failure }
        0 * _
    }

    def "can add no-op action to composite set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        expect:
        def set = ImmutableActionSet.of(action1, action2)
        set.add(Actions.doNothing()).is(set)
    }

    def "can add empty set to composite set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        expect:
        def set = ImmutableActionSet.of(action1, action2)
        set.add(ImmutableActionSet.empty()).is(set)
    }

    def "can add duplicate action to composite set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        expect:
        def set = ImmutableActionSet.of(action1, action2)
        set.add(action1).is(set)
        set.add(action2).is(set)
        set.add(ImmutableActionSet.of(action1)).is(set)
        set.add(ImmutableActionSet.of(action2)).is(set)
        ImmutableActionSet.of(action1).add(set).is(set)
    }

    def "can add duplicate actions to set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def action4 = Mock(Action)
        def action5 = Mock(Action)
        def action6 = Mock(Action)

        when:
        def set = ImmutableActionSet.of(action1, action2)
        set = set.add(action1)
        set = set.add(action2)
        set = set.add(ImmutableActionSet.of(action2, action1))
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")
        0 * _

        when:
        set = set.add(ImmutableActionSet.of(action1, action2, action3))
        set = set.add(action1)
        set = set.add(action3)
        set = set.add(ImmutableActionSet.of(action3, action2, action1))
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")
        0 * _

        when:
        set = set.add(ImmutableActionSet.of(action1, action2, action3, action4))
        set = set.add(action1)
        set = set.add(action4)
        set = set.add(ImmutableActionSet.of(action4, action2, action1))
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")
        0 * _

        when:
        set = set.add(ImmutableActionSet.of(action1, action2, action3, action4, action5))
        set = set.add(action1)
        set = set.add(action5)
        set = set.add(ImmutableActionSet.of(action5, action2, action1))
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")
        0 * _

        when:
        set = set.add(ImmutableActionSet.of(action1, action2, action3, action4, action5, action6))
        set = set.add(action1)
        set = set.add(action6)
        set = set.add(ImmutableActionSet.of(action6, action2, action1))
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")

        then:
        1 * action5.execute("value")

        then:
        1 * action6.execute("value")
        0 * _
    }

    def "can add self to composite set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        expect:
        def set = ImmutableActionSet.of(action1, action2)
        set.add(set).is(set)
    }

    def "can add action to composite set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)

        when:
        def set = ImmutableActionSet.of(action1, action2).add(action3)

        then:
        !set.empty

        when:
        set.execute("value")

        then:
        1 * action1.execute("value")
        1 * action2.execute("value")
        1 * action3.execute("value")
        0 * _
    }

    def "can merge empty set into empty set"() {
        def empty = ImmutableActionSet.empty()

        expect:
        def set = empty.mergeFrom(empty)
        set.is(empty)
    }

    def "can merge empty set into singleton set"() {
        def action = Mock(Action)
        def singleton = ImmutableActionSet.of(action)

        expect:
        def set = singleton.mergeFrom(ImmutableActionSet.empty())
        set.is(singleton)
    }

    def "can merge singleton set into empty set"() {
        def action = Mock(Action)
        def singleton = ImmutableActionSet.of(action)

        expect:
        def set = ImmutableActionSet.empty().mergeFrom(singleton)
        set.is(singleton)
    }

    def "can merge singleton set into itself"() {
        def action = Mock(Action)
        def original = ImmutableActionSet.of(action)

        expect:
        def set = original.mergeFrom(original)
        set.is(original)
    }

    def "can merge singleton set into singleton set"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def set1 = ImmutableActionSet.of(action1)
        def set2 = ImmutableActionSet.of(action2)

        when:
        def set = set1.mergeFrom(set2)
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")
        0 * _
    }

    def "can merge composite set into itself"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def original = ImmutableActionSet.of(action1, action2)

        expect:
        def set = original.mergeFrom(original)
        set.is(original)
    }

    def "can merge composite sets with common ancestors"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def action4 = Mock(Action)
        def set1 = ImmutableActionSet.of(action1, action2, action3)
        def set2 = ImmutableActionSet.of(action1, action2, action4)

        when:
        def set = set1.mergeFrom(set2)
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")
        0 * _
    }

    def "can merge composite sets multiple times"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def action4 = Mock(Action)
        def set1 = ImmutableActionSet.of(action1, action2)
        def set2 = ImmutableActionSet.of(action1, action3)

        when:
        def set = set1.mergeFrom(set2)
        set2 = set2.add(action4)
        set = set.mergeFrom(set2)
        set.execute("value")

        then:
        1 * action1.execute("value")

        then:
        1 * action2.execute("value")

        then:
        1 * action3.execute("value")

        then:
        1 * action4.execute("value")
        0 * _
    }

    def "de-duplicates actions"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)

        when:
        def set = ImmutableActionSet.of(action1, action2, ImmutableActionSet.of(action3, action1)).add(ImmutableActionSet.of(action2)).add(action3)

        then:
        !set.empty

        when:
        set.execute("value")

        then:
        1 * action1.execute("value")
        1 * action2.execute("value")
        1 * action3.execute("value")
        0 * _
    }

    def "ignores no-op actions when creating a composite"() {
        expect:
        ImmutableActionSet.of(Actions.doNothing(), Actions.doNothing()).is(ImmutableActionSet.empty())
    }

    def "ignores empty sets is a composite"() {
        expect:
        ImmutableActionSet.of(ImmutableActionSet.empty(), ImmutableActionSet.empty()).is(ImmutableActionSet.empty())
    }
}
