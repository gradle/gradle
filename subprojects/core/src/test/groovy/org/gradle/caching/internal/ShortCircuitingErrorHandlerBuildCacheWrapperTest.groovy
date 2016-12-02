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

package org.gradle.caching.internal

import org.gradle.caching.BuildCache
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import spock.lang.Specification

class ShortCircuitingErrorHandlerBuildCacheWrapperTest extends Specification {
    def cacheKey = Mock(BuildCacheKey)
    def reader = Mock(BuildCacheEntryReader)
    def writer = Mock(BuildCacheEntryWriter)

    def "stops calling through after defined number of read errors"() {
        def delegate = Mock(BuildCache)
        def wrapper = new ShortCircuitingErrorHandlerBuildCacheWrapper(delegate, 2)
        boolean found

        when:
        found = wrapper.load(cacheKey, reader)
        then:
        ! found
        1 * delegate.load(cacheKey, reader) >> { throw new RuntimeException("Error") }
        0 * _

        when:
        found = wrapper.load(cacheKey, reader)
        then:
        ! found
        1 * delegate.load(cacheKey, reader) >> { throw new RuntimeException("Error") }
        1 * delegate.getDescription() >> "Test build cache"
        0 * _

        when:
        found = wrapper.load(cacheKey, reader)
        then:
        ! found
        0 * _

        when:
        found = wrapper.load(cacheKey, reader)
        then:
        ! found
        0 * _

        when:
        wrapper.close()
        then:
        1 * delegate.close()
        1 * delegate.getDescription() >> "Test build cache"
        0 * _
    }

    def "stops calling through after defined number of write errors"() {
        def delegate = Mock(BuildCache)
        def wrapper = new ShortCircuitingErrorHandlerBuildCacheWrapper(delegate, 2)

        when:
        wrapper.store(cacheKey, writer)
        then:
        1 * delegate.store(cacheKey, writer) >> { throw new RuntimeException("Error") }
        0 * _

        when:
        wrapper.store(cacheKey, writer)
        then:
        1 * delegate.store(cacheKey, writer) >> { throw new RuntimeException("Error") }
        1 * delegate.getDescription() >> "Test build cache"
        0 * _

        when:
        wrapper.store(cacheKey, writer)
        then:
        0 * _

        when:
        wrapper.store(cacheKey, writer)
        then:
        0 * _

        when:
        wrapper.close()
        then:
        1 * delegate.close()
        1 * delegate.getDescription() >> "Test build cache"
        0 * _
    }
}
