/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.state

import org.gradle.api.Task
import org.gradle.internal.Describables
import spock.lang.Specification

class NestedObjectOwnerTest extends Specification {
    def "associating declarations does not query their task owners"() {
        given:
        def first = Mock(ModelObject)
        def second = Mock(ModelObject)
        def owner = new NestedObjectOwner(first, Describables.of("first"))

        when:
        NestedObjectOwner.merge(owner, new NestedObjectOwner(second, Describables.of("second")))

        then:
        0 * first._
        0 * second._
    }

    def "multiple declarations of one task resolve to that task"() {
        given:
        def task = Mock(Task)
        def first = Stub(ModelObject) { getTaskThatOwnsThisObject() >> task }
        def second = Stub(ModelObject) { getTaskThatOwnsThisObject() >> task }
        def owner = NestedObjectOwner.merge(
            new NestedObjectOwner(first, Describables.of("first")),
            new NestedObjectOwner(second, Describables.of("second")))

        expect:
        owner.taskThatOwnsThisObject.is(task)
    }

    def "inactive declarations do not hide the effective owner"() {
        given:
        def task = Mock(Task)
        def stale = Stub(ModelObject) { getTaskThatOwnsThisObject() >> null }
        def active = Stub(ModelObject) { getTaskThatOwnsThisObject() >> task }
        def owner = NestedObjectOwner.merge(
            new NestedObjectOwner(stale, Describables.of("old")),
            new NestedObjectOwner(active, Describables.of("current")))

        expect:
        owner.taskThatOwnsThisObject.is(task)
    }

    def "different tasks are rejected when an output requests its producer"() {
        given:
        def firstTask = Stub(Task) { toString() >> "task ':first'" }
        def secondTask = Stub(Task) { toString() >> "task ':second'" }
        def first = Stub(ModelObject) { getTaskThatOwnsThisObject() >> firstTask }
        def second = Stub(ModelObject) { getTaskThatOwnsThisObject() >> secondTask }
        def owner = NestedObjectOwner.merge(
            new NestedObjectOwner(first, Describables.of("nested bean")),
            new NestedObjectOwner(second, Describables.of("another declaration")))

        when:
        owner.taskThatOwnsThisObject

        then:
        def failure = thrown(NestedObjectOwner.ConflictingOwnersException)
        failure.message == "Nested output declared by nested bean has more than one producing task: task ':first' and task ':second'. Use a separate output bean for each task."
    }
}
