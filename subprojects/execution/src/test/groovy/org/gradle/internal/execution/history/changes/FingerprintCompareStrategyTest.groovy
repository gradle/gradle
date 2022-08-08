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

package org.gradle.internal.execution.history.changes

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Iterables
import org.gradle.internal.execution.history.impl.SerializableFileCollectionFingerprint
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.EmptyCurrentFileCollectionFingerprint
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

class FingerprintCompareStrategyTest extends Specification {

    private static final ABSOLUTE = AbsolutePathFingerprintCompareStrategy.INSTANCE
    private static final NORMALIZED = NormalizedPathFingerprintCompareStrategy.INSTANCE
    private static final IGNORED_PATH = IgnoredPathCompareStrategy.INSTANCE
    private static final ALL_STRATEGIES = ImmutableList.of(ABSOLUTE, NORMALIZED, IGNORED_PATH)

    def "empty snapshots (#strategy.class.simpleName)"() {
        expect:
        changes(strategy,
            [:],
            [:]
        ) as List == []

        where:
        strategy << ALL_STRATEGIES
    }

    def "trivial addition (#strategy.class.simpleName)"() {
        expect:
        changes(strategy,
            ["new/one": fingerprint("one")],
            [:]
        ) as List == results

        where:
        strategy     | results
        NORMALIZED   | [added("new/one": "one")]
        IGNORED_PATH | [added("new/one": "one")]
        ABSOLUTE     | [added("new/one": "one")]
    }

    def "non-trivial addition (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED,
            ["new/one": fingerprint("one"), "new/two": fingerprint("two")],
            ["old/one": fingerprint("one")]
        ) == [added("new/two": "two")]
    }

    def "non-trivial addition with absolute paths (AbsolutePathFingerprintCompareStrategy)"() {
        expect:
        changes(ABSOLUTE,
            ["one": fingerprint("one"), "two": fingerprint("two")],
            ["one": fingerprint("one")]
        ) == [added("two")]
    }

    def "trivial removal (#strategy.class.simpleName)"() {
        expect:
        changes(strategy,
            [:],
            ["old/one": fingerprint("one")]
        ) as List == [removed("old/one": "one")]

        where:
        strategy << [NORMALIZED, ABSOLUTE]
    }

    def "non-trivial removal (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED,
            ["new/one": fingerprint("one")],
            ["old/one": fingerprint("one"), "old/two": fingerprint("two")]
        ) == [removed("old/two": "two")]
    }

    def "non-trivial removal with absolute paths (AbsolutePathFingerprintCompareStrategy)"() {
        expect:
        changes(ABSOLUTE,
            ["one": fingerprint("one")],
            ["one": fingerprint("one"), "two": fingerprint("two")]
        ) == [removed("two")]
    }

    def "non-trivial modification (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "new/one": fingerprint("one"),
            "new/two": fingerprint("two", 0x9876cafe)
        ], [
            "old/one": fingerprint("one"),
            "old/two": fingerprint("two", 0xface1234)
        ]) == [removed("old/two": "two"), added("new/two": "two")]
    }

    def "non-trivial modification with re-ordering and same normalized paths (UNORDERED, NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "new/two/input": fingerprint("input", 0x9876cafe),
            "new/one/input": fingerprint("input")
        ], [
            "old/one/input": fingerprint("input"),
            "old/two/input": fingerprint("input", 0xface1234)
        ]) == [removed("old/two/input": "input"), added("new/two/input": "input")]
    }

    def "non-trivial modification with absolute paths (AbsolutePathFingerprintCompareStrategy)"() {
        expect:
        changes(ABSOLUTE, [
            "one": fingerprint("one"),
            "two": fingerprint("two", 0x9876cafe)
        ], [
            "one": fingerprint("one"),
            "two": fingerprint("two", 0xface1234)
        ]) == [modified("two", FileType.RegularFile, FileType.RegularFile)]
    }

    def "trivial replacement (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED,
            ["new/two": fingerprint("two")],
            ["old/one": fingerprint("one")]
        ) == [removed("old/one": "one"), added("new/two": "two")]
    }

    def "non-trivial replacement (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "new/one": fingerprint("one"),
            "new/two": fingerprint("two"),
            "new/four": fingerprint("four")
        ], [
            "old/one": fingerprint("one"),
            "old/three": fingerprint("three"),
            "old/four": fingerprint("four")
        ]) == [removed("old/three": "three"), added("new/two": "two")]
    }

    def "non-trivial replacement with absolute paths (#strategy.class.simpleName)"() {
        expect:
        changes(ABSOLUTE, [
            "one": fingerprint("one"),
            "two": fingerprint("two"),
            "four": fingerprint("four")
        ], [
            "one": fingerprint("one"),
            "three": fingerprint("three"),
            "four": fingerprint("four")
        ]) == [added("two"), removed("three")]
    }

    def "reordering (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "new/one": fingerprint("one"),
            "new/two": fingerprint("two"),
            "new/three": fingerprint("three")
        ], [
            "old/one": fingerprint("one"),
            "old/three": fingerprint("three"),
            "old/two": fingerprint("two")
        ]) == []
    }

    def "reordering with absolute paths (AbsolutePathFingerprintCompareStrategy)"() {
        expect:
        changes(ABSOLUTE, [
            "one": fingerprint("one"),
            "two": fingerprint("two"),
            "three": fingerprint("three")
        ], [
            "one": fingerprint("one"),
            "three": fingerprint("three"),
            "two": fingerprint("two")
        ]) == []
    }

    def "detects no change when only absolute path changes (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "new-1/one": fingerprint("one"),
            "new-2/one": fingerprint("one"),
            "new/two": fingerprint("two")
        ], [
            "old-1/one": fingerprint("one"),
            "old-2/one": fingerprint("one"),
            "old/two": fingerprint("two")
        ]) == []
    }

    def "detects no change when swapping contents between files with same normalized path (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "a/input": fingerprint("input", 2),
            "b/input": fingerprint("input", 0)
        ], [
            "a/input": fingerprint("input", 0),
            "b/input": fingerprint("input", 2)
        ]) == []
    }

    def "detects no change when file move results in unchanged normalized path (NormalizedPathFingerprintCompareStrategy)"() {
        changes(NORMALIZED, [
            "moved/input": fingerprint("input", 1),
            "unchangedFile": fingerprint("unchangedFile", 2),
            "changedFile": fingerprint("changedFile", 4)
        ], [
            "original/input": fingerprint("input", 1),
            "unchangedFile": fingerprint("unchangedFile", 2),
            "changedFile": fingerprint("changedFile", 3)
        ]) == [modified("changedFile")]
    }

    def "change file content to collide with content of another file with same normalized path (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "modified-with-collision/input": fingerprint("input", 1),
            "unmodified/input": fingerprint("input", 1)
        ], [
            "modified-with-collision/input": fingerprint("input", 0),
            "unmodified/input": fingerprint("input", 1)
        ]) == [modified("modified-with-collision/input": "input")]
    }

    def "only report modifications between files with the same absolute paths, otherwise report addition and/or removal (NormalizedPathFingerprintCompareStrategy)"() {
        expect:
        changes(NORMALIZED, [
            "modified-with-collision/input": fingerprint("input", 0),
            "added/input": fingerprint("input", 0),
            "modified/input": fingerprint("input", 2),
            "unmodified/input": fingerprint("input", 3),
            "moved/old/input": fingerprint("input", 3)
        ], [
            "modified-with-collision/input": fingerprint("input", 1),
            "removed/input": fingerprint("input", 1),
            "modified/input": fingerprint("input", 1),
            "unmodified/input": fingerprint("input", 3),
            "moved/new/input": fingerprint("input", 3)
        ]) == [
            modified("modified-with-collision/input": "input"),
            removed("removed/input": "input"),
            modified("modified/input": "input"),
            added("added/input": "input")
        ]
    }

    def "comparing regular snapshot to empty snapshot shows entries removed (strategy: #strategy)"() {
        def fingerprint = Mock(FileCollectionFingerprint) {
            getFingerprints() >> [
                "file1.txt": new DefaultFileSystemLocationFingerprint("file1.txt", FileType.RegularFile, TestHashCodes.hashCodeFrom(123)),
                "file2.txt": new DefaultFileSystemLocationFingerprint("file2.txt", FileType.RegularFile, TestHashCodes.hashCodeFrom(234)),
            ]
            getRootHashes() >> ImmutableMultimap.of('/dir', TestHashCodes.hashCodeFrom(456))
        }
        def emptyFingerprint = new EmptyCurrentFileCollectionFingerprint("test")
        expect:
        changes(strategy, emptyFingerprint, fingerprint).toList() == [
            DefaultFileChange.removed("file1.txt", "test", FileType.RegularFile, "file1.txt"),
            DefaultFileChange.removed("file2.txt", "test", FileType.RegularFile, "file2.txt")
        ]
        changes(strategy, fingerprint, emptyFingerprint).toList() == [
            DefaultFileChange.added("file1.txt", "test", FileType.RegularFile, "file1.txt"),
            DefaultFileChange.added("file2.txt", "test", FileType.RegularFile, "file2.txt")
        ]

        where:
        strategy << ALL_STRATEGIES
    }

    def "comparing empty fingerprints produces empty (strategy: #strategy)"() {
        expect:
        changes(strategy, new EmptyCurrentFileCollectionFingerprint("test"), new EmptyCurrentFileCollectionFingerprint("test"),).empty

        where:
        strategy << ALL_STRATEGIES
    }

    def changes(FingerprintCompareStrategy strategy, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous) {
        def strategyConfigurationHash = TestHashCodes.hashCodeFrom(5432)
        def currentFingerprint = new SerializableFileCollectionFingerprint(current, ImmutableMultimap.of("some", TestHashCodes.hashCodeFrom(1234)), strategyConfigurationHash)
        def previousFingerprint = new SerializableFileCollectionFingerprint(previous, ImmutableMultimap.of("some", TestHashCodes.hashCodeFrom(4321)), strategyConfigurationHash)
        changes(strategy, currentFingerprint, previousFingerprint)
    }

    def changes(FingerprintCompareStrategy strategy, FileCollectionFingerprint currentFingerprint, FileCollectionFingerprint previousFingerprint) {
        def visitor = new CollectingChangeVisitor()
        strategy.visitChangesSince(previousFingerprint, currentFingerprint, "test", visitor)
        visitor.getChanges().toList()
    }

    def fingerprint(String normalizedPath, def hashCode = 0x1234abcd) {
        return new DefaultFileSystemLocationFingerprint(normalizedPath, FileType.RegularFile, TestHashCodes.hashCodeFrom((int) hashCode))
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
