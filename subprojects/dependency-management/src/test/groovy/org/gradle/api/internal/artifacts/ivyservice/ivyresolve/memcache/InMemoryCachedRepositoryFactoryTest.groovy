/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class InMemoryCachedRepositoryFactoryTest extends Specification {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()
    def cache = new InMemoryCachedRepositoryFactory()

    def "can be turned off via system property"() {
        System.properties.setProperty(InMemoryCachedRepositoryFactory.TOGGLE_PROPERTY, "false")
        def repo = Mock(ModuleComponentRepository) { getId() >> "mavenCentral" }

        when:
        def out = cache.cached(repo)

        then:
        out.is(repo)
    }

    def "wraps repositories"() {
        def repo1 = Mock(ModuleComponentRepository) { getId() >> "mavenCentral" }
        def repo2 = Mock(ModuleComponentRepository) { getId() >> "localRepo" }
        def repo3 = Mock(ModuleComponentRepository) { getId() >> "mavenCentral" }

        when:
        ModuleComponentRepository c1 = cache.cached(repo1)
        ModuleComponentRepository c2 = cache.cached(repo2)
        ModuleComponentRepository c3 = cache.cached(repo3)

        then:
        c1.delegate == repo1
        c2.delegate == repo2
        c3.delegate == repo3

        // Caches are shared for same repository id
        c1.localAccess.artifactsCache == c3.localAccess.artifactsCache
        c1.localAccess.metaDataCache == c3.localAccess.metaDataCache
        c1.remoteAccess.artifactsCache == c3.remoteAccess.artifactsCache
        c1.remoteAccess.metaDataCache == c3.remoteAccess.metaDataCache

        c2.localAccess.artifactsCache != c1.localAccess.artifactsCache

        cache.cachePerRepo.size() == 2
    }

    def "cleans cache on close"() {
        when:
        cache.cached(Mock(ModuleComponentRepository) { getId() >> "x"} )
        cache.stop()

        then:
        cache.cachePerRepo.isEmpty()
    }
}
