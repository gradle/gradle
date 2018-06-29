/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.resource.local.FileAccessTracker
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.BuildCommencedTimeProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class DefaultModuleArtifactCacheTest extends Specification {

    @Rule TestNameTestDirectoryProvider folder = new TestNameTestDirectoryProvider()

    CacheLockingManager cacheLockingManager = Mock(CacheLockingManager)
    BuildCommencedTimeProvider timeProvider = Mock(BuildCommencedTimeProvider)
    PersistentIndexedCache persistentIndexedCache = Mock(PersistentIndexedCache)
    CachedArtifact cachedArtifact = Stub(CachedArtifact)
    FileAccessTracker fileAccessTracker = Stub(FileAccessTracker)
    String persistentCacheFile = "cacheFile"

    @Subject DefaultModuleArtifactCache index = new DefaultModuleArtifactCache(persistentCacheFile, timeProvider, cacheLockingManager, fileAccessTracker)

    def "storing null artifactFile not supported"() {
        given:
        def key = new ArtifactAtRepositoryKey("RepoID", Stub(ModuleComponentArtifactIdentifier))

        when:
        index.store(key, null, 0)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "artifactFile cannot be null"
    }

    def "artifact key must be provided"() {
        when:
        index.store(null, Stub(File), 0)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "key cannot be null"
    }

    def "stored artifact is put into persistentIndexedCache"() {
        given:
        1 * cacheLockingManager.createCache(persistentCacheFile, _, _) >> persistentIndexedCache
        def key = new ArtifactAtRepositoryKey("RepoID", Stub(ModuleComponentArtifactIdentifier))
        def testFile = folder.createFile("aTestFile")

        when:
        index.store(key, testFile, BigInteger.TEN)

        then:
        1 * cacheLockingManager.useCache(_) >> { Runnable action -> action.run() }
        1 * timeProvider.currentTime >> 123
        1 * persistentIndexedCache.put(key, _) >> { k, v ->
            assert v.cachedAt == 123
            assert v.descriptorHash == BigInteger.TEN
            assert v.cachedFile == testFile
        }
    }

    def "lookup key must not be null"() {
        when:
        index.lookup(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "key cannot be null"
    }

    def "loads missing CachedArtifact from persistentIndexedCache"() {
        given:
        def key = createEntryInPersistentCache()
        _ * cachedArtifact.missing >> true

        when:
        def fromCache = index.lookup(key)

        then:
        fromCache == cachedArtifact
        fromCache.isMissing()
    }

    def "loads CachedArtifact with file ref from persistentIndexedCache"() {
        given:
        def key = createEntryInPersistentCache()
        _ * cachedArtifact.missing >> false
        File cachedFile = Stub(File) {
            exists() >> true
        }
        cachedArtifact.getCachedFile() >> cachedFile

        when:
        def fromCache = index.lookup(key)

        then:
        fromCache == cachedArtifact
        !fromCache.isMissing()
        fromCache.cachedFile == cachedArtifact.cachedFile
    }

    def createEntryInPersistentCache() {
        1 * cacheLockingManager.createCache(persistentCacheFile, _, _) >> persistentIndexedCache
        1 * cacheLockingManager.useCache(_) >> { Factory<?> factory -> factory.create()}
        def key = new ArtifactAtRepositoryKey("RepoID", Stub(ModuleComponentArtifactIdentifier))
        1 * persistentIndexedCache.get(key) >> cachedArtifact
        key
    }
}
