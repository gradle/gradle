/*
 * Copyright 2026 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotUtil
import spock.lang.Specification

class DefaultCurrentFileCollectionFingerprintTest extends Specification {
    def strategy = AbsolutePathFingerprintingStrategy.DEFAULT
    def interner = new DefaultRootFingerprintInterner()

    def fileInDir = file("/dir/a.txt", 1)
    def dir = directory("/dir", 10, fileInDir)
    def otherFile = file("/other.txt", 2)

    def "produces the same fingerprints and hash as fingerprinting all roots at once"() {
        def roots = CompositeFileSystemSnapshot.of([dir, otherFile, fileInDir])

        when:
        def fingerprint = DefaultCurrentFileCollectionFingerprint.from(roots, strategy, interner, null)

        then:
        fingerprint.fingerprints == strategy.collectFingerprints(roots)
        fingerprint.fingerprints.keySet() as List == ["/dir", "/dir/a.txt", "/other.txt"]
        fingerprint.rootHashes == SnapshotUtil.getRootHashes(roots)
        fingerprint.hash == hashOf(strategy, roots)
        fingerprint.rootFingerprints*.rootPath == ["/dir", "/other.txt", "/dir/a.txt"]
    }

    def "shares root fingerprints between collections containing the same root"() {
        when:
        def first = DefaultCurrentFileCollectionFingerprint.from(CompositeFileSystemSnapshot.of([dir, otherFile]), strategy, interner, null)
        def second = DefaultCurrentFileCollectionFingerprint.from(CompositeFileSystemSnapshot.of([otherFile, dir]), strategy, interner, null)

        then:
        first.rootFingerprints[0].is(second.rootFingerprints[1])
        first.rootFingerprints[1].is(second.rootFingerprints[0])
        first.fingerprints.keySet() as List == ["/dir", "/dir/a.txt", "/other.txt"]
        second.fingerprints.keySet() as List == ["/other.txt", "/dir", "/dir/a.txt"]
    }

    def "does not share root fingerprints between strategies"() {
        def otherStrategy = new AbsolutePathFingerprintingStrategy(DirectorySensitivity.IGNORE_DIRECTORIES)

        when:
        def first = DefaultCurrentFileCollectionFingerprint.from(dir, strategy, interner, null)
        def second = DefaultCurrentFileCollectionFingerprint.from(dir, otherStrategy, interner, null)

        then:
        !first.rootFingerprints[0].is(second.rootFingerprints[0])
        first.fingerprints.keySet() as List == ["/dir", "/dir/a.txt"]
        second.fingerprints.keySet() as List == ["/dir/a.txt"]
    }

    def "reuses the hash of a candidate with the same roots"() {
        def roots = CompositeFileSystemSnapshot.of([dir, otherFile])
        def previous = DefaultCurrentFileCollectionFingerprint.from(roots, strategy, interner, null)
        def candidate = Stub(FileCollectionFingerprint) {
            wasCreatedWithStrategy(strategy) >> true
            getRootFingerprints() >> previous.rootFingerprints
            getHash() >> TestHashCodes.hashCodeFrom(42)
        }

        when:
        def sameRoots = DefaultCurrentFileCollectionFingerprint.from(roots, strategy, interner, candidate)
        def differentRoots = DefaultCurrentFileCollectionFingerprint.from(dir, strategy, interner, candidate)

        then:
        sameRoots.hash == TestHashCodes.hashCodeFrom(42)
        differentRoots.hash == hashOf(strategy, dir)
    }

    def "is empty when no root has fingerprints"() {
        def missing = new MissingFileSnapshot("/missing", AccessType.DIRECT)

        expect:
        DefaultCurrentFileCollectionFingerprint.from(missing, strategy, interner, null).empty
        DefaultCurrentFileCollectionFingerprint.from(FileSystemSnapshot.EMPTY, strategy, interner, null).empty
    }

    private static RegularFileSnapshot file(String path, int hash) {
        new RegularFileSnapshot(path, path.substring(path.lastIndexOf('/') + 1), TestHashCodes.hashCodeFrom(hash), DefaultFileMetadata.file(0, 0, AccessType.DIRECT))
    }

    private static DirectorySnapshot directory(String path, int hash, FileSystemLocationSnapshot... children) {
        new DirectorySnapshot(path, path.substring(path.lastIndexOf('/') + 1), AccessType.DIRECT, TestHashCodes.hashCodeFrom(hash), ImmutableList.copyOf(children))
    }

    private static hashOf(FingerprintingStrategy strategy, FileSystemSnapshot roots) {
        def hasher = Hashing.newHasher()
        strategy.hashingStrategy.appendToHasher(hasher, strategy.collectFingerprints(roots).values())
        hasher.hash()
    }
}
