/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection
import org.gradle.api.Action
import spock.lang.Specification

class CompositeUpToDateStateTest extends Specification {

    final TaskUpToDateState state1 = Mock()
    final TaskUpToDateState state2 = Mock()
    final CompositeUpToDateState state = new CompositeUpToDateState(state1, state2)
    final Action<TaskUpToDateStateChange> action = Mock()

    def delegatesToEachStateInOrder() {
        when:
        state.findChanges(action)

        then:
        1 * state1.findChanges(_)
        1 * state1.isUpToDate() >> true

        then:
        1 * state2.findChanges(_)
        1 * state2.isUpToDate() >> true
        0 * _

        when:
        state.snapshotAfterTask()

        then:
        1 * state1.snapshotAfterTask()
        1 * state2.snapshotAfterTask()
    }

    def checkUpToDateStopsAtFirstRuleWhichMarksTaskOutOfDate() {
        when:
        state.findChanges(action)

        then:
        1 * state1.findChanges(_)
        1 * state1.isUpToDate() >> false
        0 * _
    }
}
