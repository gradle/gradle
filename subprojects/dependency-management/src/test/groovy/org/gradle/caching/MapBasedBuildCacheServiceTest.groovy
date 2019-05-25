/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching

import spock.lang.Specification

import java.util.concurrent.ConcurrentMap

class MapBasedBuildCacheServiceTest extends Specification {
    def delegate = Mock(ConcurrentMap)
    def cache = new MapBasedBuildCacheService(delegate)
    def hashCode = "123456"
    def cacheKey = Stub(BuildCacheKey) {
        getHashCode() >> hashCode
    }
    def reader = Mock(BuildCacheEntryReader)
    def writer = Mock(BuildCacheEntryWriter)
    def data = [1, 2, 3] as byte[]

    def "can read from map"() {
        when:
        def found = cache.load(cacheKey, reader)
        then:
        found
        1 * delegate.get(hashCode) >> data
        1 * reader.readFrom(_) >> { InputStream input ->
            assert input.bytes == data
        }
        0 * _
    }

    def "handles missing entry"() {
        when:
        def found = cache.load(cacheKey, reader)
        then:
        ! found
        1 * delegate.get(hashCode) >> null
        0 * _
    }

    def "can write to map"() {
        when:
        cache.store(cacheKey, writer)
        then:
        1 * writer.writeTo(_) >> { OutputStream output ->
            output.write(data)
        }
        1 * delegate.put(hashCode, _) >> { String key, byte[] value ->
            assert value == data
        }
        0 * _
    }
}
