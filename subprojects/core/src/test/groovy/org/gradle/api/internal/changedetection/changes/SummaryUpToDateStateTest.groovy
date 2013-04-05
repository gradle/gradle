/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.api.internal.changedetection.changes
import org.gradle.api.Action
import spock.lang.Specification

class SummaryUpToDateStateTest extends Specification {

    def state1 = Mock(TaskUpToDateState)
    def state2 = Mock(TaskUpToDateState)
    def state = new SummaryUpToDateState(2, state1, state2)
    final Action<TaskUpToDateChange> action = Mock()
    def change = Mock(TaskUpToDateChange)

    def looksForChangesInAllDelegateChangeSets() {
        when:
        state.findChanges(action)

        then:
        1 * state1.findChanges(!null)

        then:
        1 * state2.findChanges(!null)
        0 * _
    }

    def delegatesSnapshotToAllDelegateChangeSets() {
        when:
        state.snapshotAfterTask()

        then:
        1 * state1.snapshotAfterTask()
        1 * state2.snapshotAfterTask()
        0 * _
    }

    def onlyReturnsChangesFromASingleDelegate() {
        def change1 = Mock(TaskUpToDateChange)

        when:
        state.findChanges(action)

        then:
        1 * state1.findChanges(!null) >> { UpToDateChangeListener listener -> listener.accept(change1) }
        0 * state2._

        and:
        1 * action.execute(change1)
        0 * _
    }

    def willNotAcceptMoreChangesThanSpecified() {
        def change1 = Mock(TaskUpToDateChange)
        def change2 = Mock(TaskUpToDateChange)

        when:
        state.findChanges(action)

        then:
        1 * state1.findChanges(!null)
        1 * state2.findChanges(!null) >> {UpToDateChangeListener listener ->
            listener.accept(change1)
            assert listener.accepting
            listener.accept(change2)
            assert !listener.accepting
        }

        and:
        1 * action.execute(change1)
        1 * action.execute(change2)
        0 * _
    }
}
