/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.changedetection.rules.CollectingTaskStateChangeVisitor
import org.gradle.api.internal.changedetection.rules.FileChange
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.ORDERED
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.compareTrivialSnapshots

class TaskFilePropertyCompareStrategyTest extends Specification {

    @Unroll
    def "empty snapshots (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            [:]
        ) as List == []

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one")],
            [:]
        ) as List == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [added("one-new")]
        ORDERED   | false        | []
        UNORDERED | true         | [added("one-new")]
        UNORDERED | false        | []
    }

    @Unroll
    def "non-trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [added("two-new")]
        ORDERED   | false        | []
        UNORDERED | true         | [added("two-new")]
        UNORDERED | false        | []
    }

    @Unroll
    def "non-trivial addition with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two")],
            ["one": snapshot("one")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [added("two")]
        ORDERED   | false        | []
        UNORDERED | true         | [added("two")]
        UNORDERED | false        | []
    }

    @Unroll
    def "trivial removal (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            ["one-old": snapshot("one")]
        ) as List == [removed("one-old")]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial removal (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one")],
            ["one-old": snapshot("one"), "two-old": snapshot("two")]
        ) == [removed("two-old")]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial removal with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one")],
            ["one": snapshot("one"), "two": snapshot("two")]
        ) == [removed("two")]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial modification (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two", 0x9876cafe)],
            ["one-old": snapshot("one"), "two-old": snapshot("two", 0xface1234)]
        ) == [modified("two-new", FileType.RegularFile, FileType.RegularFile)]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial modification with re-ordering and same normalized paths (UNORDERED, include added: #includeAdded)"() {
        expect:
        changes(UNORDERED, includeAdded,
            ["two-new": snapshot("", 0x9876cafe), "one-new": snapshot("")],
            ["one-old": snapshot(""), "two-old": snapshot("", 0xface1234)]
        ) == [modified("two-new", FileType.RegularFile, FileType.RegularFile)]

        where:
        includeAdded << [true, false]
    }

    @Unroll
    def "non-trivial modification with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two", 0x9876cafe)],
            ["one": snapshot("one"), "two": snapshot("two", 0xface1234)]
        ) == [modified("two", FileType.RegularFile, FileType.RegularFile)]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "trivial replacement (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) as List == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [removed("one-old"), added("two-new")]
        ORDERED   | false        | [removed("one-old")]
        UNORDERED | true         | [removed("one-old"), added("two-new")]
        UNORDERED | false        | [removed("one-old")]
    }

    @Unroll
    def "non-trivial replacement (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "four-new": snapshot("four")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "four-old": snapshot("four")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [removed("three-old"), added("two-new")]
        ORDERED   | false        | [removed("three-old")]
        UNORDERED | true         | [removed("three-old"), added("two-new")]
        UNORDERED | false        | [removed("three-old")]
    }

    @Unroll
    def "non-trivial replacement with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two"), "four": snapshot("four")],
            ["one": snapshot("one"), "three": snapshot("three"), "four": snapshot("four")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [removed("three"), added("two")]
        ORDERED   | false        | [removed("three")]
        UNORDERED | true         | [added("two"), removed("three")]
        UNORDERED | false        | [removed("three")]
    }

    @Unroll
    def "reordering (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "three-new": snapshot("three")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "two-old": snapshot("two")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [removed("three-old"), added("two-new"), removed("two-old"), added("three-new")]
        ORDERED   | false        | [removed("three-old"), removed("two-old")]
        UNORDERED | true         | []
        UNORDERED | false        | []
    }

    @Unroll
    def "reordering with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two"), "three": snapshot("three")],
            ["one": snapshot("one"), "three": snapshot("three"), "two": snapshot("two")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [removed("three"), added("two"), removed("two"), added("three")]
        ORDERED   | false        | [removed("three"), removed("two")]
        UNORDERED | true         | []
        UNORDERED | false        | []
    }

    @Unroll
    def "handling duplicates (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new-1": snapshot("one"), "one-new-2": snapshot("one"), "two-new": snapshot("two")],
            ["one-old-1": snapshot("one"), "one-old-2": snapshot("one"), "two-old": snapshot("two")]
        ) == []

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "too many elements not handled by trivial comparison (#current.size() current vs #previous.size() previous)"() {
        expect:
        compareTrivialSnapshots(current, previous, "test", true) == null
        compareTrivialSnapshots(current, previous, "test", false) == null

        where:
        current                                          | previous
        [:]                                              | ["one": snapshot("one"), "two": snapshot("two")]
        ["one": snapshot("one"), "two": snapshot("two")] | [:]
    }

    def changes(TaskFilePropertyCompareStrategy strategy, boolean includeAdded, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        def visitor = new CollectingTaskStateChangeVisitor()
        strategy.accept(visitor, current, previous, "test", false, includeAdded)
        visitor.getChanges().toList()
    }

    def changesUsingAbsolutePaths(TaskFilePropertyCompareStrategy strategy, boolean includeAdded, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        def visitor = new CollectingTaskStateChangeVisitor()
        strategy.accept(visitor, current, previous, "test", true, includeAdded)
        visitor.getChanges().toList()
    }

    def snapshot(String normalizedPath, def hashCode = 0x1234abcd) {
        return new DefaultNormalizedFileSnapshot(normalizedPath, new FileHashSnapshot(HashCode.fromInt((int) hashCode)))
    }

    def added(String path) {
        FileChange.added(path, "test", FileType.RegularFile)
    }

    def removed(String path) {
        FileChange.removed(path, "test", FileType.RegularFile)
    }

    def modified(String path, FileType previous, FileType current) {
        FileChange.modified(path, "test", previous, current)
    }
}
