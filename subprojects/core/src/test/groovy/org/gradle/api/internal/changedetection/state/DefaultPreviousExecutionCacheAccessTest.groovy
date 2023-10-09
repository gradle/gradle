/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection.state


import org.gradle.cache.CacheBuilder
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory
import spock.lang.Specification

class DefaultPreviousExecutionCacheAccessTest extends Specification {
    final BuildScopedCacheBuilderFactory cacheBuilderFactory = Mock()

    def "opens backing cache on construction"() {
        CacheBuilder cacheBuilder = Mock()
        PersistentCache backingCache = Mock()

        when:
        new DefaultExecutionHistoryCacheAccess(cacheBuilderFactory)

        then:
        1 * cacheBuilderFactory.createCacheBuilder("executionHistory") >> cacheBuilder
        1 * cacheBuilder.withDisplayName(_) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.OnDemandExclusive)) >> cacheBuilder
        1 * cacheBuilder.open() >> backingCache
        0 * _._
    }
}
