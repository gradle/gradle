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

import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.serialize.Serializer
import spock.lang.Specification

class CacheBackedFileSnapshotRepositoryTest extends Specification {
    final TaskArtifactStateCacheAccess cacheAccess = Mock()
    final PersistentIndexedCache<Object, Object> indexedCache = Mock()
    final IdGenerator<Long> idGenerator = Mock()
    final Serializer<FileCollectionSnapshot> serializer = Mock()
    FileSnapshotRepository repository

    def setup() {
        1 * cacheAccess.createCache("fileSnapshots", _, _) >> indexedCache
        repository = new CacheBackedFileSnapshotRepository(cacheAccess, serializer, idGenerator)
    }

    def "assigns an id when a snapshot is added"() {
        FileCollectionSnapshot snapshot = Mock()

        when:
        def id = repository.add(snapshot)

        then:
        id == 15
        1 * idGenerator.generateId() >> 15L
        1 * indexedCache.put(15, snapshot)
        0 * _._
    }

    def "can fetch a snapshot by id"() {
        FileCollectionSnapshot snapshot = Mock()

        when:
        def result = repository.get(4)

        then:
        result == snapshot
        1 * indexedCache.get(4) >> snapshot
        0 * _._
    }

    def "can delete a snapshot by id"() {
        when:
        repository.remove(4)

        then:
        1 * indexedCache.remove(4)
        0 * _._
    }
}
