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

import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import spock.lang.Specification
import spock.lang.Unroll

abstract class AbstractRoleAwareBuildCacheServiceDecoratorTest extends Specification {
    def key = Mock(BuildCacheKey)
    def reader = Mock(BuildCacheEntryReader)
    def writer = Mock(BuildCacheEntryWriter)
    def delegate = Mock(RoleAwareBuildCacheService)

    abstract BuildCacheService getDecorator()

    List getExceptions() {
        [ new RuntimeException(), new BuildCacheException() ]
    }

    @Unroll
    def "does not suppress #exceptionType exceptions from load"() {
        given:
        delegate.load(key, reader) >> { throw exception }
        when:
        decorator.load(key, reader)
        then:
        thrown(exception.class)
        where:
        exception << exceptions
        exceptionType = exception.class.simpleName
    }

    @Unroll
    def "does not suppress #exceptionType exceptions from store"() {
        given:
        delegate.store(key, writer) >> { throw exception }
        when:
        decorator.store(key, writer)
        then:
        thrown(exception.class)
        where:
        exception << exceptions
        exceptionType = exception.class.simpleName
    }

    def "delegates to delegate"() {
        when:
        decorator.close()
        then:
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
}
