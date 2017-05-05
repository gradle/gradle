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

import org.gradle.api.GradleException
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import spock.lang.Specification

class ShortCircuitingErrorHandlerBuildCacheServiceDecoratorTest extends Specification {
    def key = Mock(BuildCacheKey)
    def reader = Mock(BuildCacheEntryReader)
    def writer = Mock(BuildCacheEntryWriter)
    def delegate = Mock(RoleAwareBuildCacheService)
    def maxFailures = 2
    def decorator = new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(maxFailures, delegate)

    BuildCacheService getDecorator() {
        return decorator
    }

    def "does not suppress exceptions from load"() {
        given:
        delegate.load(key, reader) >> { throw new RuntimeException() }
        when:
        decorator.load(key, reader)
        then:
        def exception = thrown(GradleException.class)
        exception.message.contains key.toString()
    }

    def "does suppress exceptions from store"() {
        given:
        delegate.store(key, writer) >> { throw new RuntimeException() }
        when:
        decorator.store(key, writer)
        then:
        noExceptionThrown()
    }

    def "delegates to delegate"() {
        when:
        decorator.close()
        then:
        _ * delegate.getRole()
        1 * delegate.close()

        when:
        decorator.getDescription()
        then:
        1 * delegate.getDescription()

        when:
        decorator.store(key, writer)
        then:
        1 * delegate.store(key, writer)

        when:
        decorator.load(key, reader)
        then:
        1 * delegate.load(key, reader)
    }

    def "stops calling through after defined number of read errors"() {
        when:
        (maxFailures + 1).times {
            decorator.load(key, reader)
        }
        decorator.store(key, writer)

        then:
        maxFailures * delegate.load(key, reader) >> { throw new BuildCacheException("Error") }
        _ * delegate.getRole() >> "role"
        0 * _
    }

    def "stops calling through after defined number of write errors"() {
        when:
        (maxFailures + 1).times {
            decorator.store(key, writer)
        }
        decorator.load(key, reader)

        then:
        maxFailures * delegate.store(key, writer) >> { throw new BuildCacheException("Error") }
        _ * delegate.getRole() >> "role"
        0 * _
    }

    def "load returns false if the delegate throws BuildCacheException"() {
        delegate.load(key, reader) >> { throw new BuildCacheException() }
        expect:
        !decorator.load(key, reader)
    }

    def "close only closes once"() {
        when:
        decorator.close()
        decorator.close()
        decorator.close()
        then:
        1 * delegate.close()
    }
}
