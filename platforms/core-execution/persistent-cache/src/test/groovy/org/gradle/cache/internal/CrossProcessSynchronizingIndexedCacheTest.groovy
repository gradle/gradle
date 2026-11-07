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

package org.gradle.cache.internal

import org.gradle.cache.CrossProcessCacheAccess
import spock.lang.Specification

import java.util.function.Predicate
import java.util.function.Supplier

class CrossProcessSynchronizingIndexedCacheTest extends Specification {
    def cacheAccess = Mock(CrossProcessCacheAccess)
    def target = Mock(MultiProcessSafeAsyncPersistentIndexedCache<String, String>)
    def cache = new CrossProcessSynchronizingIndexedCache<String, String>(target, cacheAccess)

    def "conditional update holds cross-process file lock for whole operation"() {
        def condition = Mock(Predicate<String>)

        when:
        def stored = cache.putIf("key", "new", condition)

        then:
        1 * cacheAccess.withFileLock(_ as Supplier) >> { Supplier action -> action.get() }
        1 * target.putIf("key", "new", condition) >> true
        stored
        0 * cacheAccess.acquireFileLock()
        0 * _
    }

    def "conditional update releases result only after target rejects under lock"() {
        def condition = Mock(Predicate<String>)

        when:
        def stored = cache.putIf("key", "new", condition)

        then:
        1 * cacheAccess.withFileLock(_ as Supplier) >> { Supplier action -> action.get() }
        1 * target.putIf("key", "new", condition) >> false
        !stored
        0 * cacheAccess.acquireFileLock()
        0 * _
    }
}
