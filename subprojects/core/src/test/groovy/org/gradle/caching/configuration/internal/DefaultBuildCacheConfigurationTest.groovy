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

import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.BuildCache
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultBuildCacheConfigurationTest extends Specification {
    def instantiator = Mock(Instantiator) {
        newInstance(DirectoryBuildCache) >> { Stub(DirectoryBuildCache) }
    }

    def 'can reconfigure remote'() {
        def buildCacheConfiguration = createConfig()
        def original = Stub(CustomBuildCache)
        when:
        buildCacheConfiguration.remote(CustomBuildCache) { config ->
            original = config
        }
        then:
        buildCacheConfiguration.remote == original
        1 * instantiator.newInstance(CustomBuildCache) >> original
        0 * _

        BuildCache updated = null
        when:
        buildCacheConfiguration.remote(CustomBuildCache) { config ->
            updated = config
        }
        then:
        updated == original
        0 * _
    }

    def 'can reconfigure remote as super-type'() {
        def buildCacheConfiguration = createConfig()
        def original = Stub(CustomBuildCache)
        when:
        buildCacheConfiguration.remote(CustomBuildCache) { config ->
            original = config
        }
        then:
        buildCacheConfiguration.remote == original
        1 * instantiator.newInstance(CustomBuildCache) >> original
        0 * _

        BuildCache updated = null
        when:
        buildCacheConfiguration.remote(BuildCache) { config ->
            updated = config
        }
        then:
        updated == original
        0 * _
    }

    def 'can replace remote configuration completely'() {
        def buildCacheConfiguration = createConfig()
        def original = Stub(CustomBuildCache)
        when:
        buildCacheConfiguration.remote(CustomBuildCache) {}
        then:
        buildCacheConfiguration.remote == original
        1 * instantiator.newInstance(CustomBuildCache) >> original
        0 * _

        def updated = Stub(OtherCustomBuildCache)
        when:
        buildCacheConfiguration.remote(OtherCustomBuildCache) {}
        then:
        buildCacheConfiguration.remote == updated
        1 * instantiator.newInstance(OtherCustomBuildCache) >> updated
        0 * _
    }

    def 'fails when trying to reconfigure non-existent remote'() {
        def buildCacheConfiguration = createConfig()
        when:
        buildCacheConfiguration.remote {}
        then:
        def ex = thrown IllegalStateException
        ex.message == "A type for the remote build cache must be configured first."
    }

    static abstract class CustomBuildCache extends AbstractBuildCache {}

    static abstract class OtherCustomBuildCache extends AbstractBuildCache {}

    private def createConfig() {
        return new DefaultBuildCacheConfiguration(instantiator, [
            new DefaultBuildCacheServiceRegistration(DirectoryBuildCache, DirectoryBuildCacheServiceFactory),
            new DefaultBuildCacheServiceRegistration(CustomBuildCache, BuildCacheServiceFactory),
            new DefaultBuildCacheServiceRegistration(OtherCustomBuildCache, BuildCacheServiceFactory)
        ])
    }
}
