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

package org.gradle.internal.fingerprint.classpath.impl

import org.gradle.internal.change.CollectingChangeVisitor
import org.gradle.internal.change.DefaultFileChange
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.FingerprintCompareStrategy
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Unroll

class ClasspathFingerprintCompareStrategyTest extends Specification {

    private static final CLASSPATH = ClasspathCompareStrategy.INSTANCE

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
    }

    @Unroll
    def "trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": fingerprint("one")],
            [:]
        ) as List == results

        where:
        strategy     | includeAdded | results
        CLASSPATH    | true         | [added("one-new", "one")]
        CLASSPATH    | false        | []
    }

    @Unroll
    def "non-trivial addition (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": fingerprint("one"), "two-new": fingerprint("two")],
            ["one-old": fingerprint("one")]
        ) == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [added("two-new", "two")]
        CLASSPATH  | false        | []
    }

    @Unroll
    def "trivial removal (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            [:],
            ["one-old": fingerprint("one")]
        ) as List == [removed("one-old", "one")]

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
    }

    @Unroll
    def "non-trivial removal (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": fingerprint("one")],
            ["one-old": fingerprint("one"), "two-old": fingerprint("two")]
        ) == [removed("two-old", "two")]

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
    }

    @Unroll
    def "non-trivial modification (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": fingerprint("one"), "two-new": fingerprint("two", 0x9876cafe)],
            ["one-old": fingerprint("one"), "two-old": fingerprint("two", 0xface1234)]
        ) == [modified("two-new", FileType.RegularFile, FileType.RegularFile, "two")]

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
    }

    @Unroll
    def "trivial replacement (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["two-new": fingerprint("two")],
            ["one-old": fingerprint("one")]
        ) as List == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [removed("one-old", "one"), added("two-new", "two")]
        CLASSPATH  | false        | [removed("one-old", "one")]
    }

    @Unroll
    def "non-trivial replacement (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": fingerprint("one"), "two-new": fingerprint("two"), "four-new": fingerprint("four")],
            ["one-old": fingerprint("one"), "three-old": fingerprint("three"), "four-old": fingerprint("four")]
        ) == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [removed("three-old", "three"), added("two-new", "two")]
        CLASSPATH  | false        | [removed("three-old", "three")]
    }

    @Unroll
    def "reordering (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new": fingerprint("one"), "two-new": fingerprint("two"), "three-new": fingerprint("three")],
            ["one-old": fingerprint("one"), "three-old": fingerprint("three"), "two-old": fingerprint("two")]
        ) == results

        where:
        strategy   | includeAdded | results
        CLASSPATH  | true         | [removed("three-old", "three"), added("two-new", "two"), removed("two-old", "two"), added("three-new", "three")]
        CLASSPATH  | false        | [removed("three-old", "three"), removed("two-old", "two")]
    }

    @Unroll
    def "handling duplicates (#strategy, include added: #includeAdded)"() {
        expect:
        changes(strategy, includeAdded,
            ["one-new-1": fingerprint("one"), "one-new-2": fingerprint("one"), "two-new": fingerprint("two")],
            ["one-old-1": fingerprint("one"), "one-old-2": fingerprint("one"), "two-old": fingerprint("two")]
        ) == []

        where:
        strategy   | includeAdded
        CLASSPATH  | true
        CLASSPATH  | false
    }

    def "addition of jar elements"() {
        expect:
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456)],
            [jar1: jar(1234), jar3: jar(3456)]
        ) == [added("jar2")]
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)],
            [jar1: jar(1234), jar4: jar(4567), jar5: jar(5678)]
        ) == [added("jar2"), added("jar3")]
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)],
            [jar1: jar(1234), jar3: jar(3456), jar5: jar(5678)]
        ) == [added("jar2"), added("jar4")]
    }

    def "removal of jar elements"() {
        expect:
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar3: jar(3456)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456)]
        ) == [removed("jar2")]
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar4: jar(4567), jar5: jar(5678)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)]
        ) == [removed("jar2"), removed("jar3")]
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar3: jar(3456), jar5: jar(5678)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678)]
        ) == [removed("jar2"), removed("jar4")]
    }

    def "modification of jar in same path"() {
        expect:
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(4567), jar4: jar(5678)],
            [jar1: jar(1234), jar2: jar(3456), jar3: jar(4568), jar4: jar(5678)]
        ) == [modified("jar2"), modified("jar3")]
    }

    def "jar never modified for different paths"() {
        expect:
        changes(CLASSPATH, true,
            [jar1: jar(1234), 'new-jar2': jar(2345), jar3: jar(4567), jar4: jar(5678)],
            [jar1: jar(1234), 'old-jar2': jar(3456), jar3: jar(4568), jar4: jar(5678)]
        ) == [removed("old-jar2"), added("new-jar2"), modified("jar3")]
    }

    def "complex jar changes"() {
        expect:
        changes(CLASSPATH, true,
            [jar2: jar(2345), jar1: jar(1234), jar3: jar(3456), jar5: jar(5680), jar7: jar(7890)],
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678), jar6: jar(6789), jar7: jar(7890)]
        ) == [removed('jar1'), added('jar2'), removed('jar2'), added('jar1'), removed('jar4'), modified('jar5'), removed('jar6')]
        changes(CLASSPATH, true,
            [jar1: jar(1234), jar2: jar(2345), jar3: jar(3456), jar4: jar(4567), jar5: jar(5678), jar6: jar(6789), jar7: jar(7890)],
            [jar2: jar(2345), jar1: jar(1234), jar3: jar(3456), jar5: jar(5680), jar7: jar(7890)]
        ) == [removed('jar2'), added('jar1'), removed('jar1'), added('jar2'), added('jar4'), modified('jar5'), added('jar6')]
    }

    def changes(FingerprintCompareStrategy strategy, boolean includeAdded, Map<String, FileSystemLocationFingerprint> current, Map<String, FileSystemLocationFingerprint> previous) {
        def visitor = new CollectingChangeVisitor()
        strategy.visitChangesSince(visitor, current, previous, "test", includeAdded)
        visitor.getChanges().toList()
    }

    def fingerprint(String normalizedPath, def hashCode = 0x1234abcd) {
        return new DefaultFileSystemLocationFingerprint(normalizedPath, FileType.RegularFile, HashCode.fromInt((int) hashCode))
    }

    def jar(int hashCode) {
        return new DefaultFileSystemLocationFingerprint("", FileType.RegularFile, HashCode.fromInt(hashCode))
    }

    def added(String path, String normalizedPath = "") {
        DefaultFileChange.added(path, "test", FileType.RegularFile, normalizedPath)
    }

    def removed(String path, String normalizedPath = "") {
        DefaultFileChange.removed(path, "test", FileType.RegularFile, normalizedPath)
    }

    def modified(String path, FileType previous = FileType.RegularFile, FileType current = FileType.RegularFile, String normalizedPath = "") {
        DefaultFileChange.modified(path, "test", previous, current, normalizedPath)
    }
}
