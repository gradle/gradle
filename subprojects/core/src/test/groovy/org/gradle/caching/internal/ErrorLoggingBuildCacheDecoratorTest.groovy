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

class ErrorLoggingBuildCacheDecoratorTest extends Specification {
    def delegate = Mock(BuildCache)
    def key = Mock(BuildCacheKey)
    def writer = Mock(BuildCacheEntryWriter)
    def reader = Mock(BuildCacheEntryReader)
    def decorator = new ErrorLoggingBuildCacheDecorator(delegate)

    def "delegates to delegate"() {
        when:
        decorator.close()
        then:
        1 * delegate.close()
        0 * _
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

    def "load returns false if the delegate throws BuildCacheException"() {
        delegate.load(key, reader) >> { throw new BuildCacheException() }
        expect:
        !decorator.load(key, reader)
    }

    def "store does not throw an exception if the delegate throws BuildCacheException"() {
        delegate.store(key, writer) >> { throw new BuildCacheException() }
        when:
        decorator.store(key, writer)
        then:
        noExceptionThrown()
    }

    def "does not suppress non-BuildCacheException exceptions from load"() {
        delegate.load(key, reader) >> { throw new RuntimeException("no load") }
        when:
        decorator.load(key, reader)
        then:
        RuntimeException e = thrown()
        e.message == "no load"
    }

    def "does not suppress non-BuildCacheException exceptions from store"() {
        delegate.store(key, writer) >> { throw new RuntimeException("no store") }
        when:
        decorator.store(key, writer)
        then:
        RuntimeException e = thrown()
        e.message == "no store"
    }
}
