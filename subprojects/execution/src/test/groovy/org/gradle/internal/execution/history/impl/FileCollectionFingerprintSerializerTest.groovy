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

package org.gradle.internal.execution.history.impl

import com.google.common.collect.ImmutableMultimap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.SerializerSpec

class FileCollectionFingerprintSerializerTest extends SerializerSpec {

    def stringInterner = new StringInterner()
    def serializer = new FileCollectionFingerprintSerializer(stringInterner)

    def "reads and writes emtpy fingerprints"() {
        when:
        def out = serialize(FileCollectionFingerprint.EMPTY, serializer)

        then:
        out == FileCollectionFingerprint.EMPTY
    }

    def "reads and writes fingerprints"() {
        def hash = HashCode.fromInt(1234)

        def rootHashes = ImmutableMultimap.of(
                "/1", FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE,
                "/2", HashCode.fromInt(5678),
                "/3", HashCode.fromInt(1234))
        when:
        def out = serialize(new SerializableFileCollectionFingerprint(
                '/1': new DefaultFileSystemLocationFingerprint("1", FileType.Directory, FileSystemLocationFingerprint.DIR_SIGNATURE),
                '/2': IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, hash),
                '/3': new DefaultFileSystemLocationFingerprint("/3", FileType.Missing, FileSystemLocationFingerprint.DIR_SIGNATURE),
                rootHashes
        ), serializer)

        then:
        out.fingerprints.size() == 3
        out.fingerprints['/1'].with {
            type == FileType.Directory
            normalizedPath == "1"
            normalizedContentHash == FileSystemLocationFingerprint.DIR_SIGNATURE
        }
        out.fingerprints['/2'].with {
            type == FileType.RegularFile
            normalizedPath == ""
            normalizedContentHash == hash
        }
        out.fingerprints['/3'].with {
            type == FileType.Missing
            normalizedPath == "/3"
            normalizedContentHash == FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE
        }
        out.rootHashes == rootHashes
    }

    def "should retain order in serialization"() {
        when:
        def out = serialize(new SerializableFileCollectionFingerprint(
                "/3": new DefaultFileSystemLocationFingerprint('3', FileType.RegularFile, HashCode.fromInt(1234)),
                "/2": new DefaultFileSystemLocationFingerprint('/2', FileType.RegularFile, HashCode.fromInt(5678)),
                "/1": new DefaultFileSystemLocationFingerprint('1', FileType.Missing, FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE),
                ImmutableMultimap.of(
                        "/3", HashCode.fromInt(1234),
                        "/2", HashCode.fromInt(5678),
                        "/1", FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE)
        ), serializer)

        then:
        out.fingerprints.keySet() as List == ["/3", "/2", "/1"]
        out.rootHashes.keySet() as List == ["/3", "/2", "/1"]
    }
}
