/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.history.changes


import spock.lang.Specification

class TrivialChangeDetectorTest extends Specification {
    def itemComparator = Mock(TrivialChangeDetector.ItemComparator)
    def changeFactory = Mock(CompareStrategy.ChangeFactory)
    def delegate = Mock(CompareStrategy.ChangeDetector)
    def detector = new TrivialChangeDetector(itemComparator, changeFactory, delegate)

    def change = Mock(Change)
    def visitor = Mock(ChangeVisitor)

    def "detects no change between empty current and previous"() {
        when:
        detector.visitChangesSince([:], [:], "test", visitor)
        then:
        0 * _
    }

    def "detects no change between the same one item"() {
        when:
        detector.visitChangesSince([one: 1], [one: 1], "test", visitor)
        then:
        1 * itemComparator.hasSamePath(1, 1) >> true
        1 * itemComparator.hasSameContent(1, 1) >> true
        0 * _
    }

    def "detects normalized path modified"() {
        when:
        detector.visitChangesSince([one: 1], [one: -1], "test", visitor)
        then:
        1 * itemComparator.hasSamePath(1, -1) >> false
        then:
        1 * changeFactory.removed('one', 'test', 1) >> change
        1 * visitor.visitChange(change) >> true
        then:
        1 * changeFactory.added("one", "test", -1) >> change
        1 * visitor.visitChange(change) >> true
        0 * _
    }

    def "detects normalized content modified"() {
        when:
        detector.visitChangesSince([one: 1], [one: -1], "test", visitor)
        then:
        1 * itemComparator.hasSamePath(1, -1) >> true
        1 * itemComparator.hasSameContent(1, -1) >> false
        then:
        1 * changeFactory.modified("one", "test", 1, -1) >> change
        then:
        1 * visitor.visitChange(change) >> true
        0 * _
    }

    def "detects item added"() {
        when:
        detector.visitChangesSince([:], [one: 1], "test", visitor)
        then:
        1 * changeFactory.added("one", "test", 1) >> change
        then:
        1 * visitor.visitChange(change) >> true
        0 * _
    }

    def "detects multiple items added to empty"() {
        when:
        detector.visitChangesSince([:], [one: 1, two: 2], "test", visitor)
        then:
        1 * changeFactory.added("one", "test", 1) >> change
        then:
        1 * visitor.visitChange(change) >> true
        then:
        1 * changeFactory.added("two", "test", 2) >> change
        then:
        1 * visitor.visitChange(change) >> true
        0 * _
    }

    def "detects item removed"() {
        when:
        detector.visitChangesSince([one: 1], [:], "test", visitor)
        then:
        1 * changeFactory.removed("one", "test", 1) >> change
        then:
        1 * visitor.visitChange(change) >> true
        0 * _
    }

    def "detects all items removed"() {
        when:
        detector.visitChangesSince([one: 1, two: 2], [:], "test", visitor)
        then:
        1 * changeFactory.removed("one", "test", 1) >> change
        then:
        1 * visitor.visitChange(change) >> true
        then:
        1 * changeFactory.removed("two", "test", 2) >> change
        then:
        1 * visitor.visitChange(change) >> true
        0 * _
    }

    def "too many elements are delegated (#current.size() current vs #previous.size() previous items)"() {
        when:
        detector.visitChangesSince(previous, current, "test", visitor)
        then:
        1 * delegate.visitChangesSince(previous, current, "test", visitor)
        0 * _

        where:
        current          | previous
        [one: 1]         | [one: 1, two: 2]
        [one: 1, two: 2] | [one: 1]
    }
}
