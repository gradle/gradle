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

package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts

import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Subject

class TwoStageArtifactsCacheTest extends Specification {
    def timeProvider = Stub(BuildCommencedTimeProvider)
    def readCache = Mock(AbstractArtifactsCache)
    def writeCache = Mock(AbstractArtifactsCache)
    def key = Stub(ArtifactsAtRepositoryKey)

    @Subject
    TwoStageArtifactsCache twoStageArtifactsCache = new TwoStageArtifactsCache(timeProvider, readCache, writeCache)

    def "reads first in write cache then in read cache"() {
        when:
        twoStageArtifactsCache.get(key)

        then:
        1 * writeCache.get(key) >> null
        1 * readCache.get(key) >> Stub(AbstractArtifactsCache.ModuleArtifactsCacheEntry)

        when:
        twoStageArtifactsCache.get(key)

        then:
        1 * writeCache.get(key) >> Stub(AbstractArtifactsCache.ModuleArtifactsCacheEntry)
        0 * readCache.get(key)
    }

    def "writes are delegated to the writable cache"() {
        def entry = Stub(AbstractArtifactsCache.ModuleArtifactsCacheEntry)
        when:
        twoStageArtifactsCache.store(key, entry)

        then:
        writeCache.store(key, entry)
        0 * readCache.store(key, entry)
    }
}
