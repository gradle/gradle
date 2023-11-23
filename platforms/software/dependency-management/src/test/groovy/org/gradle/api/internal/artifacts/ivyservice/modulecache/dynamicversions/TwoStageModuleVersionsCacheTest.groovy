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

package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions

import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Subject

class TwoStageModuleVersionsCacheTest extends Specification {
    def timeProvider = Stub(BuildCommencedTimeProvider)
    def readCache = Mock(AbstractModuleVersionsCache)
    def writeCache = Mock(AbstractModuleVersionsCache)
    def key = Stub(ModuleAtRepositoryKey)
    def entry = Stub(ModuleVersionsCacheEntry)

    @Subject
    def twoStageCache = new TwoStageModuleVersionsCache(timeProvider, readCache, writeCache)

    def "writing delegates to write cache"() {
        when:
        twoStageCache.store(key, entry)

        then:
        1 * writeCache.store(key, entry)
        0 * readCache._
    }

    def "reading aggregates read and write caches"() {
        def r1 = new ModuleVersionsCacheEntry(["1.0", "1.1"] as Set, 0L)
        def r2 = new ModuleVersionsCacheEntry(["1.2", "1.3"]  as Set, 123L)

        when:
        def result = twoStageCache.get(key)

        then:
        1 * readCache.get(key) >> {
            r1
        }
        1 * writeCache.get(key) >> {
            r2
        }
        result.moduleVersionListing == ["1.0", "1.1", "1.2", "1.3"] as Set
        result.createTimestamp == 123L
    }
}
