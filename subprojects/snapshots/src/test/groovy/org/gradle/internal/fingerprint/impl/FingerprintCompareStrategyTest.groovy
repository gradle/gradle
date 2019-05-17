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

package org.gradle.internal.fingerprint.impl

import com.google.common.collect.Iterables
import org.gradle.internal.change.CollectingChangeVisitor
import org.gradle.internal.change.DefaultFileChange
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.FingerprintCompareStrategy
import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Unroll

import static AbstractFingerprintCompareStrategy.compareTrivialFingerprints

class FingerprintCompareStrategyTest extends Specification {

    private static final ABSOLUTE = AbsolutePathFingerprintCompareStrategy.INSTANCE
    private static final NORMALIZED = NormalizedPathFingerprintCompareStrategy.INSTANCE
    private static final IGNORED_PATH = IgnoredPathCompareStrategy.INSTANCE

    @Unroll
    def "empty snapshots (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            [:]
        ) as List == []

        where:
        strategy     | includeAdded
        NORMALIZED   | true
        IGNORED_PATH | true
        IGNORED_PATH | false
        ABSOLUTE     | true
        ABSOLUTE     | false
    }

    @Unroll
    def "trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["new/one": fingerprint("one")],
            [:]
        ) as List == results

        where:
        strategy     | includeAdded | results
        NORMALIZED   | true         | [added("new/one": "one")]
        IGNORED_PATH | true         | [added("new/one": "one")]
        IGNORED_PATH | false        | []
        ABSOLUTE     | true         | [added("new/one": "one")]
        ABSOLUTE     | false        | []
    }

    @Unroll
    def "non-trivial addition"() {
        expect:
        changes(NORMALIZED, true,
            ["new/one": fingerprint("one"), "new/two": fingerprint("two")],
            ["old/one": fingerprint("one")]
        ) == [added("new/two": "two")]
    }

    @Unroll
    def "non-trivial addition with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one": fingerprint("one"), "two": fingerprint("two")],
            ["one": fingerprint("one")]
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
            ["old/one": fingerprint("one")]
        ) as List == [removed("old/one": "one")]

        where:
        strategy   | includeAdded
        NORMALIZED | true
        ABSOLUTE   | true
        ABSOLUTE   | false
    }

    @Unroll
    def "non-trivial removal"() {
        expect:
        changes(NORMALIZED, true,
            ["new/one": fingerprint("one")],
            ["old/one": fingerprint("one"), "old/two": fingerprint("two")]
        ) == [removed("old/two": "two")]
    }

    @Unroll
    def "non-trivial removal with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one": fingerprint("one")],
            ["one": fingerprint("one"), "two": fingerprint("two")]
        ) == [removed("two")]

        where:
        strategy | includeAdded
        ABSOLUTE | true
        ABSOLUTE | false
    }

    @Unroll
    def "non-trivial modification"() {
        expect:
        changes(NORMALIZED, true,
            ["new/one": fingerprint("one"), "new/two": fingerprint("two", 0x9876cafe)],
            ["old/one": fingerprint("one"), "old/two": fingerprint("two", 0xface1234)]
        ) == [modified("old/two": "two", FileType.RegularFile, FileType.RegularFile)]
    }

    @Unroll
    def "non-trivial modification with re-ordering and same normalized paths (UNORDERED)"() {
        expect:
        changes(NORMALIZED, true,
            ["new/two": fingerprint("", 0x9876cafe), "new/one": fingerprint("")],
            ["old/one": fingerprint(""), "old/two": fingerprint("", 0xface1234)]
        ) == [modified("old/two": "", FileType.RegularFile, FileType.RegularFile)]
    }

    @Unroll
    def "non-trivial modification with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one": fingerprint("one"), "two": fingerprint("two", 0x9876cafe)],
            ["one": fingerprint("one"), "two": fingerprint("two", 0xface1234)]
        ) == [modified("two", FileType.RegularFile, FileType.RegularFile)]

        where:
        strategy | includeAdded
        ABSOLUTE | true
        ABSOLUTE | false
    }

    @Unroll
    def "trivial replacement"() {
        expect:
        changes(NORMALIZED, true,
            ["new/two": fingerprint("two")],
            ["old/one": fingerprint("one")]
        ) == [removed("old/one": "one"), added("new/two": "two")]
    }

    @Unroll
    def "non-trivial replacement"() {
        expect:
        changes(NORMALIZED, true,
            ["new/one": fingerprint("one"), "new/two": fingerprint("two"), "new/four": fingerprint("four")],
            ["old/one": fingerprint("one"), "old/three": fingerprint("three"), "old/four": fingerprint("four")]
        ) == [removed("old/three": "three"), added("new/two": "two")]
    }

    @Unroll
    def "non-trivial replacement with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one": fingerprint("one"), "two": fingerprint("two"), "four": fingerprint("four")],
            ["one": fingerprint("one"), "three": fingerprint("three"), "four": fingerprint("four")]
        ) == results

        where:
        strategy | includeAdded | results
        ABSOLUTE | true         | [added("two"), removed("three")]
        ABSOLUTE | false        | [removed("three")]
    }

    @Unroll
    def "reordering (#strategy, include added: #includeAdded)"() {
        expect:
        changes(NORMALIZED, true,
            ["new/one": fingerprint("one"), "new/two": fingerprint("two"), "new/three": fingerprint("three")],
            ["old/one": fingerprint("one"), "old/three": fingerprint("three"), "old/two": fingerprint("two")]
        ) == []
    }

    @Unroll
    def "reordering with absolute paths (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one": fingerprint("one"), "two": fingerprint("two"), "three": fingerprint("three")],
            ["one": fingerprint("one"), "three": fingerprint("three"), "two": fingerprint("two")]
        ) == results

        where:
        strategy | includeAdded | results
        ABSOLUTE | true         | []
        ABSOLUTE | false        | []
    }

    @Unroll
    def "handling duplicates"() {
        expect:
        changes(NORMALIZED, true,
            ["new/one-1": fingerprint("one"), "new/one-2": fingerprint("one"), "new/two": fingerprint("two")],
            ["old/one-1": fingerprint("one"), "old/one-2": fingerprint("one"), "old/two": fingerprint("two")]
        ) == []
    }

    @Unroll
    def "too many elements not handled by trivial comparison (#current.size() current vs #previous.size() previous)"() {
        expect:
        compareTrivialFingerprints(new CollectingChangeVisitor(), current, previous, "test", true) == null
        compareTrivialFingerprints(new CollectingChangeVisitor(), current, previous, "test", false) == null

        where:
        current                                                | previous
        ["one": fingerprint("one")]                            | ["one": fingerprint("one"), "two": fingerprint("two")]
        ["one": fingerprint("one"), "two": fingerprint("two")] | ["one": fingerprint("one")]
    }

    def changes(FingerprintCompareStrategy strategy, boolean includeAdded, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous) {
        def visitor = new CollectingChangeVisitor()
        strategy.visitChangesSince(visitor, current, previous, "test", includeAdded)
        visitor.getChanges().toList()
    }

    def fingerprint(String normalizedPath, def hashCode = 0x1234abcd) {
        return new DefaultFileSystemLocationFingerprint(normalizedPath, FileType.RegularFile, HashCode.fromInt((int) hashCode))
    }

    def added(String path) {
        added((path): path)
    }

    def added(Map<String, String> entry) {
        def singleEntry = Iterables.getOnlyElement(entry.entrySet())
        DefaultFileChange.added(singleEntry.key, "test", FileType.RegularFile, singleEntry.value)
    }

    def removed(String path) {
        removed((path): path)
    }

    def removed(Map<String, String> entry) {
        def singleEntry = Iterables.getOnlyElement(entry.entrySet())
        DefaultFileChange.removed(singleEntry.key, "test", FileType.RegularFile, singleEntry.value)
    }

    def modified(String path, FileType previous = FileType.RegularFile, FileType current = FileType.RegularFile) {
        modified((path): path, previous, current)
    }

    def modified(Map<String, String> paths, FileType previous = FileType.RegularFile, FileType current = FileType.RegularFile) {
        def singleEntry = Iterables.getOnlyElement(paths.entrySet())
        DefaultFileChange.modified(singleEntry.key, "test", previous, current, singleEntry.value)
    }
}
