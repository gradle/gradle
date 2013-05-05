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


package org.gradle.api.internal.changedetection.rules

import spock.lang.Specification

class SummaryTaskStateChangesTest extends Specification {

    def state1 = Mock(TaskStateChanges)
    def state2 = Mock(TaskStateChanges)
    def state = new SummaryTaskStateChanges(2, state1, state2)
    def change = Mock(TaskStateChange)

    def looksForChangesInAllDelegateChangeSets() {
        when:
        def hasNext = state.iterator().hasNext()

        then:
        1 * state1.iterator() >> [].iterator()
        1 * state2.iterator() >> [].iterator()
        0 * _

        and:
        !hasNext
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
        def change1 = Mock(TaskStateChange)

        when:
        def it = state.iterator()
        it.hasNext()

        then:
        1 * state1.iterator() >> [change1].iterator()
        0 * _

        and:
        it.hasNext()
        it.next() == change1
        !it.hasNext()
    }

    def willNotEmitMoreChangesThanSpecified() {
        def change1 = Mock(TaskStateChange)
        def change2 = Mock(TaskStateChange)
        def change3 = Mock(TaskStateChange)

        when:
        def it = state.iterator()
        it.hasNext()

        then:
        1 * state1.iterator() >> [].iterator()
        1 * state2.iterator() >> [change1, change2, change3].iterator()

        and:
        it.hasNext()
        it.next() == change1
        it.hasNext()
        it.next() == change2
        !it.hasNext()
    }
}
