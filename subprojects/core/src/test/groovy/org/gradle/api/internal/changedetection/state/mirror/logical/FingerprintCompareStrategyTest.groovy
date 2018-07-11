/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical

import org.gradle.api.internal.changedetection.rules.CollectingTaskStateChangeVisitor
import org.gradle.api.internal.changedetection.rules.FileChange
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Unroll

import static FingerprintCompareStrategy.ABSOLUTE
import static FingerprintCompareStrategy.CLASSPATH
import static FingerprintCompareStrategy.IGNORED_PATH
import static FingerprintCompareStrategy.NORMALIZED
import static org.gradle.api.internal.changedetection.state.mirror.logical.FingerprintCompareStrategy.isTrivialComparison

class FingerprintCompareStrategyTest extends Specification {

    @Unroll
    def "empty snapshots (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            [:]
        ) as List == []

        where:
        strategy     | includeAdded
        CLASSPATH    | true
        CLASSPATH    | false
        NORMALIZED   | true
        NORMALIZED   | false
        IGNORED_PATH | true
        IGNORED_PATH | false
        ABSOLUTE     | true
        ABSOLUTE     | false
    }

    @Unroll
    def "trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one")],
            [:]
        ) as List == results

        where:
        strategy     | includeAdded | results
        CLASSPATH    | true         | [added("one-new")]
        CLASSPATH    | false        | []
        NORMALIZED   | true         | [added("one-new")]
        NORMALIZED   | false        | []
        IGNORED_PATH | true         | [added("one-new")]
        IGNORED_PATH | false        | []
        ABSOLUTE     | true         | [added("one-new")]
        ABSOLUTE     | false        | []
    }

    @Unroll
    def "non-trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [added("two-new")]
        CLASSPATH  | false        | []
        NORMALIZED | true         | [added("two-new")]
        NORMALIZED | false        | []
    }

    @Unroll
    def "non-trivial addition with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two")],
            ["one": snapshot("one")]
        ) == results

        where:
        strategy | includeAdded | results
        ABSOLUTE | true         | [added("two")]
        ABSOLUTE | false        | []
    }

    @Unroll
    def "trivial removal (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            ["one-old": snapshot("one")]
        ) as List == [removed("one-old")]

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
        NORMALIZED | true
        NORMALIZED | false
        ABSOLUTE   | true
        ABSOLUTE   | false
    }

    @Unroll
    def "non-trivial removal (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one")],
            ["one-old": snapshot("one"), "two-old": snapshot("two")]
        ) == [removed("two-old")]

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
        NORMALIZED | true
        NORMALIZED | false
    }

    @Unroll
    def "non-trivial removal with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one")],
            ["one": snapshot("one"), "two": snapshot("two")]
        ) == [removed("two")]

        where:
        strategy | includeAdded
        ABSOLUTE | true
        ABSOLUTE | false
    }

    @Unroll
    def "non-trivial modification (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two", 0x9876cafe)],
            ["one-old": snapshot("one"), "two-old": snapshot("two", 0xface1234)]
        ) == [modified("two-new", FileType.RegularFile, FileType.RegularFile)]

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
        NORMALIZED | true
        NORMALIZED | false
    }

    @Unroll
    def "non-trivial modification with re-ordering and same normalized paths (UNORDERED, include added: #includeAdded)"() {
        expect:
        changes(NORMALIZED, includeAdded,
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
        strategy | includeAdded
        ABSOLUTE | true
        ABSOLUTE | false
    }

    @Unroll
    def "trivial replacement (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["two-new": snapshot("two")],
            ["one-old": snapshot("one")]
        ) as List == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [removed("one-old"), added("two-new")]
        CLASSPATH  | false        | [removed("one-old")]
        NORMALIZED | true         | [removed("one-old"), added("two-new")]
        NORMALIZED | false        | [removed("one-old")]
    }

    @Unroll
    def "non-trivial replacement (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "four-new": snapshot("four")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "four-old": snapshot("four")]
        ) == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [removed("three-old"), added("two-new")]
        CLASSPATH  | false        | [removed("three-old")]
        NORMALIZED | true         | [removed("three-old"), added("two-new")]
        NORMALIZED | false        | [removed("three-old")]
    }

    @Unroll
    def "non-trivial replacement with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two"), "four": snapshot("four")],
            ["one": snapshot("one"), "three": snapshot("three"), "four": snapshot("four")]
        ) == results

        where:
        strategy | includeAdded | results
        ABSOLUTE | true         | [added("two"), removed("three")]
        ABSOLUTE | false        | [removed("three")]
    }

    @Unroll
    def "reordering (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": snapshot("one"), "two-new": snapshot("two"), "three-new": snapshot("three")],
            ["one-old": snapshot("one"), "three-old": snapshot("three"), "two-old": snapshot("two")]
        ) == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [removed("three-old"), added("two-new"), removed("two-old"), added("three-new")]
        CLASSPATH  | false        | [removed("three-old"), removed("two-old")]
        NORMALIZED | true         | []
        NORMALIZED | false        | []
    }

    @Unroll
    def "reordering with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changesUsingAbsolutePaths(strategy, includeAdded,
            ["one": snapshot("one"), "two": snapshot("two"), "three": snapshot("three")],
            ["one": snapshot("one"), "three": snapshot("three"), "two": snapshot("two")]
        ) == results

        where:
        strategy | includeAdded | results
        ABSOLUTE | true         | []
        ABSOLUTE | false        | []
    }

    @Unroll
    def "handling duplicates (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new-1": snapshot("one"), "one-new-2": snapshot("one"), "two-new": snapshot("two")],
            ["one-old-1": snapshot("one"), "one-old-2": snapshot("one"), "two-old": snapshot("two")]
        ) == []

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
        NORMALIZED | true
        NORMALIZED | false
    }

    @Unroll
    def "too many elements not handled by trivial comparison (#current.size() current vs #previous.size() previous)"() {
        expect:
        !isTrivialComparison(current, previous)

        where:
        current                                          | previous
        ["one": snapshot("one")]                         | ["one": snapshot("one"), "two": snapshot("two")]
        ["one": snapshot("one"), "two": snapshot("two")] | ["one": snapshot("one")]
    }

    def changes(FingerprintCompareStrategy strategy, boolean includeAdded, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        def visitor = new CollectingTaskStateChangeVisitor()
        strategy.visitChangesSince(visitor, current, previous, "test", includeAdded)
        visitor.getChanges().toList()
    }

    def changesUsingAbsolutePaths(FingerprintCompareStrategy strategy, boolean includeAdded, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous) {
        def visitor = new CollectingTaskStateChangeVisitor()
        strategy.visitChangesSince(visitor, current, previous, "test", includeAdded)
        visitor.getChanges().toList()
    }

    def snapshot(String normalizedPath, def hashCode = 0x1234abcd) {
        return new DefaultNormalizedFileSnapshot(normalizedPath, FileType.RegularFile, HashCode.fromInt((int) hashCode))
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
