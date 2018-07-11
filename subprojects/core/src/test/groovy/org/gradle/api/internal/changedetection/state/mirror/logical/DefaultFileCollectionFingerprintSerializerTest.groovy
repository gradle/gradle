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

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot
import org.gradle.api.internal.changedetection.state.NonNormalizedFileSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.IgnoredPathFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.SerializerSpec

class DefaultFileCollectionFingerprintSerializerTest extends SerializerSpec {

    def stringInterner = new StringInterner()
    def serializer = new DefaultFileCollectionFingerprint.SerializerImpl(stringInterner)

    def "reads and writes the fingerprints"(FingerprintCompareStrategy strategy) {
        def hash = HashCode.fromInt(1234)
        def combinedHash = HashCode.fromInt(5678)

        when:
        def out = serialize(new DefaultFileCollectionFingerprint(
            '/1': new DefaultNormalizedFileSnapshot("1", FileType.Directory, PhysicalDirectorySnapshot.SIGNATURE),
            '/2': IgnoredPathFingerprint.create(FileType.RegularFile, hash),
            '/3': new NonNormalizedFileSnapshot("/3", FileType.Missing, PhysicalMissingSnapshot.SIGNATURE),
            strategy,
            combinedHash
        ), serializer)

        then:
        out.snapshots.size() == 3
        out.hash == combinedHash
        out.snapshots['/1'].with {
            type == FileType.Directory
            normalizedPath == "1"
            normalizedContentHash == PhysicalDirectorySnapshot.SIGNATURE
        }
        out.snapshots['/2'].with {
            type == FileType.RegularFile
            normalizedPath == ""
            normalizedContentHash == hash
        }
        out.snapshots['/3'].with {
            type == FileType.Missing
            normalizedPath == "/3"
            normalizedContentHash == PhysicalMissingSnapshot.SIGNATURE
        }
        out.strategy == strategy

        where:
        strategy << FingerprintCompareStrategy.values()
    }

    def "should retain order in serialization"() {
        when:
        DefaultFileCollectionFingerprint out = serialize(new DefaultFileCollectionFingerprint(
            "/3": new DefaultNormalizedFileSnapshot('3', FileType.RegularFile, HashCode.fromInt(1234)),
            "/2": new NonNormalizedFileSnapshot('/2', FileType.RegularFile, HashCode.fromInt(5678)),
            "/1": new DefaultNormalizedFileSnapshot('1', FileType.Missing, PhysicalMissingSnapshot.SIGNATURE),
            FingerprintCompareStrategy.ABSOLUTE,
            null
        ), serializer)

        then:
        out.snapshots.keySet() as List == ["/3", "/2", "/1"]
    }
}
