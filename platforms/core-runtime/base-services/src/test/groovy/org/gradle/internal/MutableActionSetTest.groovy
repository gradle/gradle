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

class MutableActionSetTest extends Specification {
    final MutableActionSet<String> broadcast = new MutableActionSet<String>()

    def broadcastsEventsToAction() {
        def action = Mock(Action)
        broadcast.add(action)

        when:
        broadcast.execute('value')

        then:
        1 * action.execute('value')
        0 * action._
    }

    def broadcastsEventsToMultipleActions() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        broadcast.add(action1)
        broadcast.add(action2)

        when:
        broadcast.execute('value')

        then:
        1 * action1.execute('value')
        1 * action2.execute('value')
        0 * _._
    }

    def broadcastsEventsToMultipleActionsStopsOnFirstFailure() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def failure = new RuntimeException()

        broadcast.add(action1)
        broadcast.add(action2)
        broadcast.add(action3)

        when:
        broadcast.execute('value')

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * action1.execute('value')
        1 * action2.execute('value') >> { throw failure }
        0 * _._
    }

    def actionCanAddOtherActions() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        broadcast.add(action1)

        when:
        broadcast.execute('value')

        then:
        1 * action1.execute('value') >> { broadcast.add(action2) }
        0 * _._
    }

    def canDiscardActions() {
        def action = Mock(Action)
        broadcast.add(action)

        when:
        broadcast.clear()
        broadcast.execute('value')

        then:
        0 * action._
    }
}
