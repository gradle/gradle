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

import com.google.common.collect.ImmutableMultimap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.NormalizedFileSnapshot
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.SerializerSpec

class DefaultHistoricalFileCollectionFingerprintSerializerTest extends SerializerSpec {

    def stringInterner = new StringInterner()
    def serializer = new DefaultHistoricalFileCollectionFingerprint.SerializerImpl(stringInterner)

    def "reads and writes the fingerprints"(FingerprintCompareStrategy strategy) {
        def hash = HashCode.fromInt(1234)

        def rootHashes = ImmutableMultimap.of(
        "/1", NormalizedFileSnapshot.MISSING_FILE_SIGNATURE,
        "/2", HashCode.fromInt(5678),
        "/3", HashCode.fromInt(1234))
        when:
        def out = serialize(new DefaultHistoricalFileCollectionFingerprint(
            '/1': new DefaultNormalizedFileSnapshot("1", FileType.Directory, NormalizedFileSnapshot.DIR_SIGNATURE),
            '/2': IgnoredPathFingerprint.create(FileType.RegularFile, hash),
            '/3': new DefaultNormalizedFileSnapshot("/3", FileType.Missing, NormalizedFileSnapshot.DIR_SIGNATURE),
            strategy, rootHashes
        ), serializer)

        then:
        out.snapshots.size() == 3
        out.snapshots['/1'].with {
            type == FileType.Directory
            normalizedPath == "1"
            normalizedContentHash == NormalizedFileSnapshot.DIR_SIGNATURE
        }
        out.snapshots['/2'].with {
            type == FileType.RegularFile
            normalizedPath == ""
            normalizedContentHash == hash
        }
        out.snapshots['/3'].with {
            type == FileType.Missing
            normalizedPath == "/3"
            normalizedContentHash == NormalizedFileSnapshot.MISSING_FILE_SIGNATURE
        }
        out.compareStrategy == strategy
        out.rootHashes == rootHashes

        where:
        strategy << FingerprintCompareStrategy.values()
    }

    def "should retain order in serialization"() {
        when:
        DefaultHistoricalFileCollectionFingerprint out = serialize(new DefaultHistoricalFileCollectionFingerprint(
            "/3": new DefaultNormalizedFileSnapshot('3', FileType.RegularFile, HashCode.fromInt(1234)),
            "/2": new DefaultNormalizedFileSnapshot('/2', FileType.RegularFile, HashCode.fromInt(5678)),
            "/1": new DefaultNormalizedFileSnapshot('1', FileType.Missing, NormalizedFileSnapshot.MISSING_FILE_SIGNATURE),
            FingerprintCompareStrategy.ABSOLUTE, ImmutableMultimap.of(
            "/3", HashCode.fromInt(1234),
            "/2", HashCode.fromInt(5678),
            "/1", NormalizedFileSnapshot.MISSING_FILE_SIGNATURE)
        ), serializer)

        then:
        out.snapshots.keySet() as List == ["/3", "/2", "/1"]
        out.rootHashes.keySet() as List == ["/3", "/2", "/1"]
    }
}
