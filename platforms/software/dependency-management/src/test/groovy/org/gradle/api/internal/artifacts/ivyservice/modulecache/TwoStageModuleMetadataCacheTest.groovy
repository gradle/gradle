/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Subject

class TwoStageModuleMetadataCacheTest extends Specification {
    def timeProvider = Stub(BuildCommencedTimeProvider)
    def readCache = Mock(AbstractModuleMetadataCache)
    def writeCache = Mock(AbstractModuleMetadataCache)
    def key = Stub(ModuleComponentAtRepositoryKey)
    def entry = Stub(ModuleMetadataCacheEntry)
    def metadata = Stub(ModuleMetadataCache.CachedMetadata)

    @Subject
    def twoStageCache = new TwoStageModuleMetadataCache(timeProvider, readCache, writeCache)

    def "storing delegates to write cache"() {
        when:
        twoStageCache.store(key, entry, metadata)

        then:
        1 * writeCache.store(key, entry, metadata)
        0 * readCache._
    }

    def "reading from write cache then read cache"() {
        when:
        twoStageCache.get(key)

        then:
        1 * writeCache.get(key) >> null
        1 * readCache.get(key)

        when:
        twoStageCache.get(key)

        then:
        1 * writeCache.get(key) >> metadata
        0 * readCache._
    }
}
