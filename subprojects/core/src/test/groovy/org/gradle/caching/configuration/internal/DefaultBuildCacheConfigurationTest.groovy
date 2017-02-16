/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.configuration.internal

import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.configuration.BuildCacheServiceBuilder
import spock.lang.Specification

class DefaultBuildCacheConfigurationTest extends Specification {
    def cacheKey = Mock(BuildCacheKey)
    def reader = Mock(BuildCacheEntryReader)
    def writer = Mock(BuildCacheEntryWriter)
    def delegateService = Mock(BuildCacheService)
    def builder = Mock(BuildCacheServiceBuilder) {
        build() >> delegateService
    }

    def "no decoration happens when both pushing and pulling is enabled"() {
        when:
        def service = DefaultBuildCacheConfiguration.filterPushAndPullWhenNeeded(false, false, builder)
        then:
        service == delegateService
    }

    def "pushing can be disabled"() {
        def service = DefaultBuildCacheConfiguration.filterPushAndPullWhenNeeded(true, false, builder)

        when:
        service.load(cacheKey, reader)
        then:
        1 * delegateService.load(cacheKey, reader)
        0 * _

        when:
        service.store(cacheKey, writer)
        then:
        0 * _
    }

    def "pulling can be disabled"() {
        def service = DefaultBuildCacheConfiguration.filterPushAndPullWhenNeeded(false, true, builder)

        when:
        service.load(cacheKey, reader)
        then:
        0 * _

        when:
        service.store(cacheKey, writer)
        then:
        1 * delegateService.store(cacheKey, writer)
        0 * _
    }

    def "when both pushing and pulling are disabled nothing gets through"() {
        when:
        def service = DefaultBuildCacheConfiguration.filterPushAndPullWhenNeeded(true, true, builder)
        then:
        service == BuildCacheService.NO_OP
        0 * builder.build()

        when:
        service.load(cacheKey, reader)
        then:
        0 * _

        when:
        service.store(cacheKey, writer)
        then:
        0 * _
    }
}
