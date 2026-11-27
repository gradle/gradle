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
import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.internal.snapshot.impl.ClassImplementationSnapshot

import java.time.Duration

class DefaultPreviousExecutionStateSerializerTest extends SerializerSpec {
    def stringInterner = new StringInterner()
    def serializer = new DefaultPreviousExecutionStateSerializer(
        new FileCollectionFingerprintSerializer(stringInterner),
        new FileSystemSnapshotSerializer(stringInterner),
        new HashCodeSerializer()
    )

    def "reads and writes previous execution state with #inputPropertyHashes.size() input property hashes"() {
        def state = new DefaultPreviousExecutionState(
            new OriginMetadata("build-id", TestHashCodes.hashCodeFrom(1), Duration.ofSeconds(1)),
            TestHashCodes.hashCodeFrom(2),
            new ClassImplementationSnapshot("Work", TestHashCodes.hashCodeFrom(3)),
            ImmutableList.of(new ClassImplementationSnapshot("Action", TestHashCodes.hashCodeFrom(4))),
            ImmutableSortedMap.copyOf(inputPropertyHashes),
            ImmutableSortedMap.of("inputFiles", FileCollectionFingerprint.EMPTY),
            ImmutableSortedMap.of(),
            true
        )

        when:
        def out = serialize(state, serializer)

        then:
        out.originMetadata == state.originMetadata
        out.cacheKey == state.cacheKey
        out.implementation == state.implementation
        out.additionalImplementations == state.additionalImplementations
        out.inputPropertyHashes == state.inputPropertyHashes
        out.inputFileProperties.keySet() == state.inputFileProperties.keySet()
        out.outputFilesProducedByWork == state.outputFilesProducedByWork
        out.successful == state.successful

        where:
        inputPropertyHashes << [
            [:],
            [single: TestHashCodes.hashCodeFrom(10)],
            [first: TestHashCodes.hashCodeFrom(10), second: TestHashCodes.hashCodeFrom(20), third: TestHashCodes.hashCodeFrom(30)]
        ]
    }
}
