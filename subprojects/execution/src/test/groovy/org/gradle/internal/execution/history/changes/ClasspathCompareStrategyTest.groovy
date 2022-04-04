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

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Iterables
import org.gradle.internal.execution.history.impl.SerializableFileCollectionFingerprint
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

class ClasspathCompareStrategyTest extends Specification {

    private static final CLASSPATH = ClasspathCompareStrategy.INSTANCE

    def "empty snapshots"() {
        expect:
        changes(
            [:],
            [:]
        ) == []
    }

    def "trivial addition"() {
        expect:
        changes(
            ["one-new": fingerprint("one")],
            [:]
        ) == [added("one-new": "one")]
    }

    def "non-trivial addition"() {
        expect:
        changes(
            ["one-new": fingerprint("one"), "two-new": fingerprint("two")],
            ["one-old": fingerprint("one")]
        ) == [added("two-new": "two")]
    }

    def "trivial removal"() {
        expect:
        changes(
            [:],
            ["one-old": fingerprint("one")]
        ) == [removed("one-old": "one")]
    }

    def "non-trivial removal"() {
        expect:
        changes(
            ["one-new": fingerprint("one")],
            ["one-old": fingerprint("one"), "two-old": fingerprint("two")]
        ) == [removed("two-old": "two")]
    }

    def "non-trivial modification"() {
        expect:
        changes(
            ["one-new": fingerprint("one"), "two-new": fingerprint("two", 0x9876cafe)],
            ["one-old": fingerprint("one"), "two-old": fingerprint("two", 0xface1234)]
        ) == [modified("two-new": "two", FileType.RegularFile, FileType.RegularFile)]
    }

    def "trivial replacement"() {
        expect:
        changes(
            ["two-new": fingerprint("two")],
            ["one-old": fingerprint("one")]
        ) == [removed("one-old": "one"), added("two-new": "two")]
    }

    def "non-trivial replacement"() {
        expect:
        changes(
            ["one-new": fingerprint("one"), "two-new": fingerprint("two"), "four-new": fingerprint("four")],
            ["one-old": fingerprint("one"), "three-old": fingerprint("three"), "four-old": fingerprint("four")]
        ) == [removed("three-old": "three"), added("two-new": "two")]
    }

    def "reordering"() {
        expect:
        changes(
            ["one-new": fingerprint("one"), "two-new": fingerprint("two"), "three-new": fingerprint("three")],
            ["one-old": fingerprint("one"), "three-old": fingerprint("three"), "two-old": fingerprint("two")]
        ) == [removed("three-old": "three"), added("two-new": "two"), removed("two-old": "two"), added("three-new": "three")]
    }

    def "handling duplicates"() {
        expect:
        changes(
            ["one-new-1": fingerprint("one"), "one-new-2": fingerprint("one"), "two-new": fingerprint("two")],
            ["one-old-1": fingerprint("one"), "one-old-2": fingerprint("one"), "two-old": fingerprint("two")]
        ) == []
    }

    def "addition of jar elements"() {
        expect:
        changes(
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456)],
            [jar1: jar(1234), jar3: jar(3456)]
        ) == [added("jar2")]
        changes(
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)],
            [jar1: jar(1234), jar4: jar(4567), jar5: jar(5678)]
        ) == [added("jar2"), added("jar3")]
        changes(
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)],
            [jar1: jar(1234), jar3: jar(3456), jar5: jar(5678)]
        ) == [added("jar2"), added("jar4")]
    }

    def "removal of jar elements"() {
        expect:
        changes(
            [jar1: jar(1234), jar3: jar(3456)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456)]
        ) == [removed("jar2")]
        changes(
            [jar1: jar(1234), jar4: jar(4567), jar5: jar(5678)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)]
        ) == [removed("jar2"), removed("jar3")]
        changes(
            [jar1: jar(1234), jar3: jar(3456), jar5: jar(5678)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)]
        ) == [removed("jar2"), removed("jar4")]
    }

    def "modification of jar in same path"() {
        expect:
        changes(
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(4567), jar4: jar(5678)],
            [jar1: jar(1234), jar2: jar(3456), jar3: jar(4568), jar4: jar(5678)]
        ) == [modified("jar2"), modified("jar3")]
    }

    def "jar never modified for different paths"() {
        expect:
        changes(
            [jar1: jar(1234), 'new-jar2': jar(2345), jar3: jar(4567), jar4: jar(5678)],
            [jar1: jar(1234), 'old-jar2': jar(3456), jar3: jar(4568), jar4: jar(5678)]
        ) == [removed("old-jar2"), added("new-jar2"), modified("jar3")]
    }

    def "complex jar changes"() {
        expect:
        changes(
            [jar2: jar(2345), jar1: jar(1234), jar3: jar(3456), jar5: jar(5680), jar7: jar(7890)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678), jar6: jar(6789), jar7: jar(7890)]
        ) == [removed('jar1'), added('jar2'), removed('jar2'), added('jar1'), removed('jar4'), modified('jar5'), removed('jar6')]
        changes(
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678), jar6: jar(6789), jar7: jar(7890)],
            [jar2: jar(2345), jar1: jar(1234), jar3: jar(3456), jar5: jar(5680), jar7: jar(7890)]
        ) == [removed('jar2'), added('jar1'), removed('jar1'), added('jar2'), added('jar4'), modified('jar5'), added('jar6')]
    }

    def changes(Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous) {
        def visitor = new CollectingChangeVisitor()
        def strategyConfigurationHash = TestHashCodes.hashCodeFrom(1234)
        def currentFingerprint = new SerializableFileCollectionFingerprint(current, ImmutableMultimap.of("some", TestHashCodes.hashCodeFrom(1234)), strategyConfigurationHash)
        def previousFingerprint = new SerializableFileCollectionFingerprint(previous, ImmutableMultimap.of("some", TestHashCodes.hashCodeFrom(4321)), strategyConfigurationHash)
        CLASSPATH.visitChangesSince(previousFingerprint, currentFingerprint, "test", visitor)
        visitor.getChanges().toList()
    }

    def fingerprint(String normalizedPath, def hashCode = 0x1234abcd) {
        return new DefaultFileSystemLocationFingerprint(normalizedPath, FileType.RegularFile, TestHashCodes.hashCodeFrom((int) hashCode))
    }

    def jar(int hashCode) {
        return new DefaultFileSystemLocationFingerprint("", FileType.RegularFile, TestHashCodes.hashCodeFrom(hashCode))
    }

    def added(String path) {
        added((path): "")
    }

    def added(Map<String, String> entry) {
        def singleEntry = Iterables.getOnlyElement(entry.entrySet())
        DefaultFileChange.added(singleEntry.key, "test", FileType.RegularFile, singleEntry.value)
    }

    def removed(String path) {
        removed((path): "")
    }

    def removed(Map<String, String> entry) {
        def singleEntry = Iterables.getOnlyElement(entry.entrySet())
        DefaultFileChange.removed(singleEntry.key, "test", FileType.RegularFile, singleEntry.value)
    }

    def modified(String path, FileType previous = FileType.RegularFile, FileType current = FileType.RegularFile) {
        modified((path): "", previous, current)
    }

    def modified(Map<String, String> paths, FileType previous = FileType.RegularFile, FileType current = FileType.RegularFile) {
        def singleEntry = Iterables.getOnlyElement(paths.entrySet())
        DefaultFileChange.modified(singleEntry.key, "test", previous, current, singleEntry.value)
    }
}
