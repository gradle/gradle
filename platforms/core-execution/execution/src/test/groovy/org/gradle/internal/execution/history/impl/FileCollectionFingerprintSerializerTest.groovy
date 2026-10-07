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

package org.gradle.internal.execution.history.impl

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.RootFingerprint
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.SerializerSpec

class FileCollectionFingerprintSerializerTest extends SerializerSpec {
    def stringInterner = new StringInterner()
    def forest = new TestRootFingerprintForest()
    def serializer = new FileCollectionFingerprintSerializer(stringInterner, forest)
    def strategyConfigurationHash = TestHashCodes.hashCodeFrom(6543)

    def "reads and writes empty fingerprints"() {
        when:
        def out = serialize(FileCollectionFingerprint.EMPTY, serializer)

        then:
        out == FileCollectionFingerprint.EMPTY
    }

    def "reads and writes fingerprints as references to stored roots"() {
        def missingRoot = new RootFingerprint("/1", FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE, ImmutableMap.of(
            "/1", new DefaultFileSystemLocationFingerprint("/1", FileType.Missing, FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE)))
        def fileRoot = new RootFingerprint("/2", TestHashCodes.hashCodeFrom(5678), ImmutableMap.of(
            "/2", IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, TestHashCodes.hashCodeFrom(5678))))
        def directoryRoot = new RootFingerprint("/3", TestHashCodes.hashCodeFrom(1234), ImmutableMap.of(
            "/3", new DefaultFileSystemLocationFingerprint("3", FileType.Directory, FileSystemLocationFingerprint.DIR_SIGNATURE),
            "/3/a", new DefaultFileSystemLocationFingerprint("a", FileType.RegularFile, TestHashCodes.hashCodeFrom(42))))
        def fingerprint = new SerializableFileCollectionFingerprint(ImmutableList.of(missingRoot, fileRoot, directoryRoot), strategyConfigurationHash, TestHashCodes.hashCodeFrom(99))
        forest.store(fingerprint)

        when:
        def out = serialize(fingerprint, serializer)

        then:
        out.rootFingerprints == [missingRoot, fileRoot, directoryRoot]
        out.hash == TestHashCodes.hashCodeFrom(99)
        out.strategyConfigurationHash == strategyConfigurationHash
        out.fingerprints.keySet() as List == ["/1", "/2", "/3", "/3/a"]
        out.rootHashes.keySet() as List == ["/1", "/2", "/3"]
        out.rootHashes.values() as List == [FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE, TestHashCodes.hashCodeFrom(5678), TestHashCodes.hashCodeFrom(1234)]
    }

    def "keeps the order of roots and repeated roots"() {
        def first = new RootFingerprint("/b", TestHashCodes.hashCodeFrom(1), ImmutableMap.of(
            "/b", IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, TestHashCodes.hashCodeFrom(1))))
        def second = new RootFingerprint("/a", TestHashCodes.hashCodeFrom(2), ImmutableMap.of(
            "/a", IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, TestHashCodes.hashCodeFrom(2))))
        def fingerprint = new SerializableFileCollectionFingerprint(ImmutableList.of(first, second, first), strategyConfigurationHash, TestHashCodes.hashCodeFrom(99))
        forest.store(fingerprint)

        when:
        def out = serialize(fingerprint, serializer)

        then:
        out.rootFingerprints == [first, second, first]
        out.rootHashes.keySet() as List == ["/b", "/a"]
        out.rootHashes.get("/b").size() == 2
    }

    def "fails when a referenced root is not stored"() {
        def root = new RootFingerprint("/1", TestHashCodes.hashCodeFrom(1), ImmutableMap.of(
            "/1", IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, TestHashCodes.hashCodeFrom(1))))
        def fingerprint = new SerializableFileCollectionFingerprint(ImmutableList.of(root), strategyConfigurationHash, TestHashCodes.hashCodeFrom(99))

        when:
        def out = serialize(fingerprint, serializer)
        out.rootFingerprints

        then:
        thrown(MissingRootFingerprintException)
    }
}
