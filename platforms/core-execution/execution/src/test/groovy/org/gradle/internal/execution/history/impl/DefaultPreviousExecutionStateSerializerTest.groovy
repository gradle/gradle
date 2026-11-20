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
import com.google.common.collect.Interners
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

import java.time.Duration

class DefaultPreviousExecutionStateSerializerTest extends SerializerSpec {
    def stringInterner = Interners.newStrongInterner()
    def serializer = new DefaultPreviousExecutionStateSerializer(
        new FileCollectionFingerprintSerializer(stringInterner),
        new FileSystemSnapshotSerializer(stringInterner),
        Stub(ClassLoaderHierarchyHasher),
        new HashCodeSerializer()
    )

    def "preserves execution history entry id across serialization"() {
        def originMetadata = new OriginMetadata("build-invocation", TestHashCodes.hashCodeFrom(1234), Duration.ofMillis(25))
        def state = new DefaultPreviousExecutionState(
            "history-entry-123",
            originMetadata,
            TestHashCodes.hashCodeFrom(5678),
            ImplementationSnapshot.of("TestImplementation", TestHashCodes.hashCodeFrom(42)),
            ImmutableList.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            true
        )

        when:
        def restored = serialize(state, serializer)

        then:
        restored instanceof DefaultPreviousExecutionState
        restored.executionHistoryEntryId == "history-entry-123"
        restored.originMetadata == originMetadata
        restored.cacheKey == state.cacheKey
        restored.successful
    }
}
