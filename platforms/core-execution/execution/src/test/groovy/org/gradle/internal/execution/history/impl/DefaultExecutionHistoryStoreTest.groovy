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
import org.gradle.cache.CacheDecorator
import org.gradle.cache.MultiProcessSafeIndexedCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import spock.lang.Specification

import java.time.Duration
import java.util.function.Predicate

class DefaultExecutionHistoryStoreTest extends Specification {
    def indexedCache = Mock(MultiProcessSafeIndexedCache<String, PreviousExecutionState>)
    def cacheAccess = Stub(ExecutionHistoryCacheAccess)
    def decoratorFactory = Stub(InMemoryCacheDecoratorFactory)
    def decorator = Stub(CacheDecorator)
    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher)

    def store

    def setup() {
        decoratorFactory.decorator(10000, false) >> decorator
        cacheAccess.createIndexedCache(_) >> indexedCache

        store = new DefaultExecutionHistoryStore(
            cacheAccess,
            decoratorFactory,
            Interners.newStrongInterner(),
            classLoaderHasher
        )
    }

    def "does not store when expected history was removed"() {
        def expected = previousState("entry-1", "previous")

        when:
        def stored = store.storeIfUnchanged("work", Optional.of(expected), afterState("current"))

        then:
        1 * indexedCache.putIf("work", _ as PreviousExecutionState, _ as Predicate) >> { String key, PreviousExecutionState value, Predicate condition ->
            assert !condition.test(null)
            false
        }
        !stored
    }

    def "does not store after ABA replacement with equivalent history"() {
        def expected = previousState("entry-1", "same-origin")
        def replacement = previousState("entry-2", "same-origin")

        when:
        def stored = store.storeIfUnchanged("work", Optional.of(expected), afterState("current"))

        then:
        1 * indexedCache.putIf("work", _ as PreviousExecutionState, _ as Predicate) >> { String key, PreviousExecutionState value, Predicate condition ->
            assert !condition.test(replacement)
            false
        }
        !stored
    }

    def "stores when exact expected history entry is still current"() {
        def expected = previousState("entry-1", "previous")
        def current = previousState("entry-1", "previous")

        when:
        def stored = store.storeIfUnchanged("work", Optional.of(expected), afterState("current"))

        then:
        1 * indexedCache.putIf("work", _ as PreviousExecutionState, _ as Predicate) >> { String key, PreviousExecutionState value, Predicate condition ->
            assert condition.test(current)
            true
        }
        stored
    }

    def "stores when history was absent and remains absent"() {
        when:
        def stored = store.storeIfUnchanged("work", Optional.empty(), afterState("current"))

        then:
        1 * indexedCache.putIf("work", _ as PreviousExecutionState, _ as Predicate) >> { String key, PreviousExecutionState value, Predicate condition ->
            assert condition.test(null)
            true
        }
        stored
    }

    def "does not overwrite a concurrent store after loading absent history"() {
        def concurrentState = previousState("entry-2", "concurrent")

        when:
        def stored = store.storeIfUnchanged("work", Optional.empty(), afterState("current"))

        then:
        1 * indexedCache.putIf("work", _ as PreviousExecutionState, _ as Predicate) >> { String key, PreviousExecutionState value, Predicate condition ->
            assert !condition.test(concurrentState)
            false
        }
        !stored
    }

    private DefaultPreviousExecutionState previousState(String entryId, String buildId) {
        def cacheKey = TestHashCodes.hashCodeFrom(1234)
        def originMetadata = new OriginMetadata(buildId, cacheKey, Duration.ofMillis(10))
        return Mock(DefaultPreviousExecutionState) {
            getExecutionHistoryEntryId() >> entryId
            getCacheKey() >> cacheKey
            getOriginMetadata() >> originMetadata
            isSuccessful() >> true
        }
    }

    private AfterExecutionState afterState(String buildId) {
        def cacheKey = TestHashCodes.hashCodeFrom(5678)
        def originMetadata = new OriginMetadata(buildId, cacheKey, Duration.ofMillis(20))
        return Stub(AfterExecutionState) {
            getOriginMetadata() >> originMetadata
            getCacheKey() >> cacheKey
            getImplementation() >> ImplementationSnapshot.of("Test", TestHashCodes.hashCodeFrom(42))
            getAdditionalImplementations() >> ImmutableList.of()
            getInputProperties() >> ImmutableSortedMap.of()
            getInputFileProperties() >> ImmutableSortedMap.of()
            getOutputFilesProducedByWork() >> ImmutableSortedMap.of()
            isSuccessful() >> true
        }
    }
}
