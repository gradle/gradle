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

import com.google.common.collect.Lists
import com.google.common.hash.HashCode
import org.gradle.api.internal.changedetection.rules.ChangeType
import org.gradle.api.internal.changedetection.rules.FileChange
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.changedetection.rules.ChangeType.*
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.*

class TaskFilePropertyCompareStrategyTest extends Specification {

    @Unroll
    def "empty snapshots (#strategy)"() {
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
    def "trivial addition (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one")],
            [:]
        ) as List == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [new FileChange("one-new", ADDED, "test")]
        ORDERED   | false        | []
        UNORDERED | true         | [new FileChange("one-new", ADDED, "test")]
        UNORDERED | false        | []
    }

    @Unroll
    def "non-trivial addition (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [change("two-new", ADDED)]
        ORDERED   | false        | []
        UNORDERED | true         | [change("two-new", ADDED)]
        UNORDERED | false        | []
    }

    @Unroll
    def "non-trivial addition with absolute paths (#strategy)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two")],
            ["one": snapshot("one")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [change("two", ADDED)]
        ORDERED   | false        | []
        UNORDERED | true         | [change("two", ADDED)]
        UNORDERED | false        | []
    }

    @Unroll
    def "trivial removal (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            ["one-old": snapshot("one")]
        ) as List == [new FileChange("one-old", REMOVED, "test")]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial removal (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one")],
            ["one-old": snapshot("one"), "two-old": snapshot("two")]
        ) == [change("two-old", REMOVED)]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial removal with absolute paths (#strategy)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one")],
            ["one": snapshot("one"), "two": snapshot("two")]
        ) == [change("two", REMOVED)]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial modification (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two", "9876cafe")],
            ["one-old": snapshot("one"), "two-old": snapshot("two", "face1234")]
        ) == [change("two-new", MODIFIED)]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "non-trivial modification with absolute paths (#strategy)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two", "9876cafe")],
            ["one": snapshot("one"), "two": snapshot("two", "face1234")]
        ) == [change("two", MODIFIED)]

        where:
        strategy  | includeAdded
        ORDERED   | true
        ORDERED   | false
        UNORDERED | true
        UNORDERED | false
    }

    @Unroll
    def "trivial replacement (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) as List == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [new FileChange("one-old", REMOVED, "test"), new FileChange("two-new", ADDED, "test")]
        ORDERED   | false        | [new FileChange("one-old", REMOVED, "test")]
        UNORDERED | true         | [new FileChange("one-old", REMOVED, "test"), new FileChange("two-new", ADDED, "test")]
        UNORDERED | false        | [new FileChange("one-old", REMOVED, "test")]
    }

    @Unroll
    def "non-trivial replacement (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "four-new": snapshot("four")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "four-old": snapshot("four")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [change("three-old", REMOVED), change("two-new", ADDED)]
        ORDERED   | false        | [change("three-old", REMOVED)]
        UNORDERED | true         | [change("three-old", REMOVED), change("two-new", ADDED)]
        UNORDERED | false        | [change("three-old", REMOVED)]
    }

    @Unroll
    def "non-trivial replacement with absolute paths (#strategy)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two"), "four": snapshot("four")],
            ["one": snapshot("one"), "three": snapshot("three"), "four": snapshot("four")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [change("three", REMOVED), change("two", ADDED)]
        ORDERED   | false        | [change("three", REMOVED)]
        UNORDERED | true         | [change("three", REMOVED), change("two", ADDED)]
        UNORDERED | false        | [change("three", REMOVED)]
    }

    @Unroll
    def "reordering (#strategy)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "three-new": snapshot("three")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "two-old": snapshot("two")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [change("three-old", REMOVED), change("two-new", ADDED), change("two-old", REMOVED), change("three-new", ADDED)]
        ORDERED   | false        | [change("three-old", REMOVED), change("two-old", REMOVED)]
        UNORDERED | true         | []
        UNORDERED | false        | []
    }

    @Unroll
    def "reordering with absolute paths (#strategy)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two"), "three": snapshot("three")],
            ["one": snapshot("one"), "three": snapshot("three"), "two": snapshot("two")]
        ) == results

        where:
        strategy  | includeAdded | results
        ORDERED   | true         | [change("three", REMOVED), change("two", ADDED), change("two", REMOVED), change("three", ADDED)]
        ORDERED   | false        | [change("three", REMOVED), change("two", REMOVED)]
        UNORDERED | true         | []
        UNORDERED | false        | []
    }

    @Unroll
    def "handling duplicates (#strategy)"() {
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
        Lists.newArrayList(strategy.iterateContentChangesSince(current, previous, "test", false, includeAdded))
    }

    def changesUsingAbsolutePaths(TaskFilePropertyCompareStrategy strategy, boolean includeAdded, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        Lists.newArrayList(strategy.iterateContentChangesSince(current, previous, "test", true, includeAdded))
    }

    def snapshot(String normalizedPath, String hashCode = "1234abcd") {
        return new DefaultNormalizedFileSnapshot(normalizedPath, new FileHashSnapshot(HashCode.fromString(hashCode)))
    }

    def change(String path, ChangeType type) {
        new FileChange(path, type, "test")
    }
}
