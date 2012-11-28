/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.externalresource.ivy

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.CachingModuleVersionRepository
import org.gradle.api.internal.externalresource.cached.CachedArtifact
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData
import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.TimeProvider
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class ArtifactAtRepositoryCachedArtifactIndexTest extends Specification {

    CacheLockingManager cacheLockingManager = Mock()
    TimeProvider timeProvider = Mock()
    ArtifactAtRepositoryKey key = Mock()
    ExternalResourceMetaData metaData = Mock()
    CachingModuleVersionRepository moduleVersionRepository = Mock()

    @Rule TemporaryFolder folder = new TemporaryFolder();

    PersistentIndexedCache persistentIndexedCache = Mock()

    ArtifactAtRepositoryCachedArtifactIndex index
    File persistentCacheFile
    CachedArtifact cachedArtifact = Mock()


    def setup() {
        persistentCacheFile = folder.createFile("cacheFile")
        index = new ArtifactAtRepositoryCachedArtifactIndex(persistentCacheFile, timeProvider, cacheLockingManager)
    }

    def "storing null artifactFile not supported"() {
        when:
        index.store(key, null, 0)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "artifactFile cannot be null"
    }

    def "artifact key must be provided"() {
        when:
        index.store(null, Mock(File), 0)
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "key cannot be null"
    }

    def "stored artifact is put into persistentIndexedCache"() {
        setup:
        1 * moduleVersionRepository.getId() >> "RepoID"
        1 * cacheLockingManager.createCache(persistentCacheFile, ArtifactAtRepositoryKey.class, CachedArtifact.class) >> persistentIndexedCache
        def key = new ArtifactAtRepositoryKey(moduleVersionRepository, "artifactId");
        def testFile = folder.createFile("aTestFile");
        when:
        index.store(key, testFile, BigInteger.TEN)

        then:
        1 * cacheLockingManager.useCache("store into artifact resolution cache \'cacheFile\'", _) >> {descr, action -> action.run()}
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
        setup:
        def key = createEntryInPersistentCache()
        _ * cachedArtifact.missing >> true

        when:
        def fromCache = index.lookup(key)

        then:
        fromCache == cachedArtifact
        fromCache.isMissing()
    }

    def "loads CachedArtifact with file ref from persistentIndexedCache"() {
        setup:
        def key = createEntryInPersistentCache()
        _ * cachedArtifact.missing >> false
        File cachedFile = Mock()
        cachedFile.exists() >> true;
        cachedArtifact.getCachedFile() >> cachedFile
        when:
        def fromCache = index.lookup(key)

        then:
        fromCache == cachedArtifact
        !fromCache.isMissing()
        fromCache.cachedFile == cachedArtifact.cachedFile
    }

    def createEntryInPersistentCache() {
        1 * moduleVersionRepository.getId() >> "RepoID"
        1 * cacheLockingManager.createCache(persistentCacheFile, ArtifactAtRepositoryKey.class, CachedArtifact.class) >> persistentIndexedCache
        1 * cacheLockingManager.useCache("lookup from artifact resolution cache \'cacheFile\'", _) >> {descr, factory -> factory.create()}
        def key = new ArtifactAtRepositoryKey(moduleVersionRepository, "artifactId");
        1 * persistentIndexedCache.get(key) >> cachedArtifact;
        key
    }
}
