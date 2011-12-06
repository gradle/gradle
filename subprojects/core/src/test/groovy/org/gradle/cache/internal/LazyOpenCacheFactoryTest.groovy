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
package org.gradle.cache.internal

import spock.lang.Specification
import org.gradle.CacheUsage
import org.gradle.cache.PersistentCache
import org.gradle.cache.PersistentIndexedCache

class LazyOpenCacheFactoryTest extends Specification {
    final CacheFactory target = Mock()
    final LazyOpenCacheFactory factory = new LazyOpenCacheFactory(target)
    
    def "does not open cache when persistent cache is created"() {
        PersistentCache realCache = Mock()
        
        when:
        def cache = factory.open(new File("dir"), "<display>", CacheUsage.ON, [:], FileLockManager.LockMode.Exclusive, null)

        then:
        0 * _._

        when:
        cache.getBaseDir()

        then:
        1 * target.open(new File("dir"), "<display>", CacheUsage.ON, [:], FileLockManager.LockMode.Exclusive, null) >> realCache
        1 * realCache.getBaseDir() >> new File("base-dir")
        0 * _._
    }
    
    def "does not open cache when an indexed cache is created"() {
        PersistentCache realCache = Mock()
        PersistentIndexedCache<String, String> realIndexedCache = Mock()
        
        when:
        def cache = factory.open(new File("dir"), "<display>", CacheUsage.ON, [:], FileLockManager.LockMode.Exclusive, null)
        def indexedCache = cache.createCache(new File("indexed"), String, String)

        then:
        0 * _._

        when:
        indexedCache.get("key")

        then:
        1 * target.open(new File("dir"), "<display>", CacheUsage.ON, [:], FileLockManager.LockMode.Exclusive, null) >> realCache
        1 * realCache.createCache(new File("indexed"), String, String) >> realIndexedCache
        1 * realIndexedCache.get("key")
        0 * _._
        
    }
}
