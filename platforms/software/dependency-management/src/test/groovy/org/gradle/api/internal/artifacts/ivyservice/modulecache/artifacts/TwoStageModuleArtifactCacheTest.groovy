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

import org.gradle.internal.hash.HashCode
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class TwoStageModuleArtifactCacheTest extends Specification {
    Path readOnlyPath = Stub(Path)
    File artifact = Stub(File)
    HashCode hashCode = Stub(HashCode)
    def key = Stub(ArtifactAtRepositoryKey)

    def readCache = Mock(ModuleArtifactCache)
    def writeCache = Mock(ModuleArtifactCache)

    @Subject
    def twoStageCache = new TwoStageModuleArtifactCache(readOnlyPath, readCache, writeCache)

    def "storing delegates to the write index"() {
        when:
        twoStageCache.store(key, artifact, hashCode)

        then:
        1 * writeCache.store(key, artifact, hashCode)
        0 * readCache._
    }

    def "store missing delegates to the write index"() {
        when:
        twoStageCache.storeMissing(key, ["abc"], hashCode)

        then:
        1 * writeCache.storeMissing(key, ["abc"], hashCode)
        0 * readCache._
    }

    def "lookup searches the write index then delegates to the read index"() {
        when:
        twoStageCache.lookup(key)

        then:
        1 * writeCache.lookup(key) >> null
        1 * readCache.lookup(key)

        when:
        twoStageCache.lookup(key)

        then:
        1 * writeCache.lookup(key) >> Stub(CachedArtifact)
        0 * readCache._
    }

    def "clearing delegates to the writable cache"() {
        when:
        twoStageCache.clear(key)

        then:
        1 * writeCache.clear(key)
        0 * readCache._
    }
}
