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
import org.gradle.cache.CacheRepository
import org.gradle.cache.DirectoryCacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentIndexedCache
import spock.lang.Specification

class DefaultTaskArtifactStateCacheAccessTest extends Specification {
    final GradleInternal gradle = Mock()
    final CacheRepository cacheRepository = Mock()
    final DefaultTaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository)
    
    def "opens backing cache on first use"() {
        DirectoryCacheBuilder cacheBuilder = Mock()
        PersistentCache backingCache = Mock()
        PersistentIndexedCache<String, Integer> backingIndexedCache = Mock()

        when:
        def indexedCache = cacheAccess.createCache("some-cache", String, Integer)

        then:
        0 * _._

        when:
        indexedCache.get("key")

        then:
        1 * cacheRepository.cache("taskArtifacts") >> cacheBuilder
        1 * cacheBuilder.open() >> backingCache
        _ * cacheBuilder._ >> cacheBuilder
        _ * backingCache.baseDir >> new File("baseDir")
        1 * backingCache.createCache(new File("baseDir/some-cache.bin"), String, Integer) >> backingIndexedCache
        1 * backingIndexedCache.get("key")
        0 * _._
    }
}
