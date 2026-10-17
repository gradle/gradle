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

package org.gradle.internal.execution.history

import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.MultiProcessSafeIndexedCache
import org.gradle.cache.PersistentCache
import spock.lang.Specification

class ExecutionHistoryCacheAccessTest extends Specification {
    def persistentCache = Stub(PersistentCache)
    def cacheAccess = new ExecutionHistoryCacheAccess() {
        @Override
        PersistentCache get() {
            return persistentCache
        }
    }

    def "exposes multi-process-safe indexed cache"() {
        def indexedCache = Stub(MultiProcessSafeIndexedCache)
        def parameters = IndexedCacheParameters.of("history", String, String)
        persistentCache.createIndexedCache(parameters) >> indexedCache

        expect:
        cacheAccess.createIndexedCache(parameters).is(indexedCache)
    }

    def "rejects indexed cache without multi-process safety"() {
        def indexedCache = Stub(IndexedCache)
        def parameters = IndexedCacheParameters.of("history", String, String)
        persistentCache.createIndexedCache(parameters) >> indexedCache

        when:
        cacheAccess.createIndexedCache(parameters)

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "Execution history requires a multi-process-safe indexed cache"
    }
}
