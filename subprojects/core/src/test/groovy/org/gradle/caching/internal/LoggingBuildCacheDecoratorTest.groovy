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
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import spock.lang.Specification

class LoggingBuildCacheDecoratorTest extends Specification {
    def delegate = Mock(BuildCache)
    def key = Mock(BuildCacheKey)
    def writer = Mock(BuildCacheEntryWriter)
    def reader = Mock(BuildCacheEntryReader)
    def decorator = new LoggingBuildCacheDecorator(delegate)

    def "delegates to delegate"() {
        when:
        decorator.close()
        then:
        1 * delegate.close()

        when:
        decorator.getDescription()
        then:
        1 * delegate.getDescription()
        0 * _

        when:
        decorator.store(key, writer)
        then:
        1 * delegate.store(key, writer)
        0 * _

        when:
        decorator.load(key, reader)
        then:
        1 * delegate.load(key, reader)
        0 * _
    }

    def "does not suppress RuntimeException from load"() {
        given:
        delegate.load(key, reader) >> { throw new RuntimeException() }
        when:
        decorator.load(key, reader)
        then:
        thrown(RuntimeException)
    }

    def "does not suppress BuildCacheException from load"() {
        given:
        delegate.load(key, reader) >> { throw new BuildCacheException() }
        when:
        decorator.load(key, reader)
        then:
        thrown(BuildCacheException)
    }

    def "does not suppress RuntimeException from store"() {
        given:
        delegate.store(key, writer) >> { throw new RuntimeException() }
        when:
        decorator.store(key, writer)
        then:
        thrown(RuntimeException)
    }

    def "does not suppress BuildCacheException from store"() {
        given:
        delegate.store(key, writer) >> { throw new BuildCacheException() }
        when:
        decorator.store(key, writer)
        then:
        thrown(BuildCacheException)
    }
}
