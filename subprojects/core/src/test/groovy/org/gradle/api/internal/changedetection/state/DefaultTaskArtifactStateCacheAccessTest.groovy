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

import org.gradle.api.internal.GradleInternal
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentIndexedCache
import org.gradle.cache.internal.FileLockManager
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.messaging.serialize.DefaultSerializer
import spock.lang.Specification

class DefaultTaskArtifactStateCacheAccessTest extends Specification {
    final GradleInternal gradle = Mock()
    final CacheRepository cacheRepository = Mock()
    final DefaultTaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository, new NoOpDecorator())
    
    def "opens backing cache on first use"() {
        CacheBuilder cacheBuilder = Mock()
        PersistentCache backingCache = Mock()
        PersistentIndexedCache<String, Integer> backingIndexedCache = Mock()

        def serializer = new DefaultSerializer<Integer>()
        when:
        def indexedCache = cacheAccess.createCache("some-cache", String, serializer)

        then:
        0 * _._

        when:
        indexedCache.get("key")

        then:
        1 * cacheRepository.cache(gradle, "taskArtifacts") >> cacheBuilder
        1 * cacheBuilder.withDisplayName(_) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.None)) >> cacheBuilder
        1 * cacheBuilder.open() >> backingCache
        _ * backingCache.baseDir >> new File("baseDir")
        1 * backingCache.createCache({it.cacheFile == new File("baseDir/some-cache.bin")}) >> backingIndexedCache
        1 * backingIndexedCache.get("key")
        0 * _._
    }
}
