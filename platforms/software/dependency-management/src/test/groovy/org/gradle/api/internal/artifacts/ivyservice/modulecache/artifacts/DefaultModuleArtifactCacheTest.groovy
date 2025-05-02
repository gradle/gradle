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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.cache.IndexedCache
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.util.function.Supplier

class DefaultModuleArtifactCacheTest extends Specification {

    @Rule TestNameTestDirectoryProvider folder = new TestNameTestDirectoryProvider(getClass())

    ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator = Mock(ArtifactCacheLockingAccessCoordinator)
    BuildCommencedTimeProvider timeProvider = Mock(BuildCommencedTimeProvider)
    IndexedCache persistentIndexedCache = Mock(IndexedCache)
    CachedArtifact cachedArtifact = Stub(CachedArtifact)
    FileAccessTracker fileAccessTracker = Stub(FileAccessTracker)
    String persistentCacheFile = "cacheFile"
    Path commonRootPath = folder.createDir("common").toPath()

    @Subject DefaultModuleArtifactCache index = new DefaultModuleArtifactCache(persistentCacheFile, timeProvider, cacheAccessCoordinator, fileAccessTracker, commonRootPath)

    def "storing null artifactFile not supported"() {
        given:
        def key = new ArtifactAtRepositoryKey("RepoID", Stub(ModuleComponentArtifactIdentifier))

        when:
        index.store(key, null, TestHashCodes.hashCodeFrom(0))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "artifactFile cannot be null"
    }

    def "artifact key must be provided"() {
        when:
        index.store(null, Stub(File), TestHashCodes.hashCodeFrom(0))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "key cannot be null"
    }

    def "stored artifact is put into persistentIndexedCache"() {
        given:
        1 * cacheAccessCoordinator.createCache(persistentCacheFile, _, _) >> persistentIndexedCache
        def key = new ArtifactAtRepositoryKey("RepoID", Stub(ModuleComponentArtifactIdentifier))
        def testFile = folder.createFile("aTestFile")

        when:
        index.store(key, testFile, TestHashCodes.hashCodeFrom(10))

        then:
        1 * cacheAccessCoordinator.useCache(_) >> { Runnable action -> action.run() }
        1 * timeProvider.currentTime >> 123
        1 * persistentIndexedCache.put(key, _) >> { k, v ->
            assert v.cachedAt == 123
            assert v.descriptorHash == TestHashCodes.hashCodeFrom(10)
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

    def "value serializer can relativize path"() {
        given:
        Serializer<CachedArtifact> valueSerializer = new DefaultModuleArtifactCache.CachedArtifactSerializer(commonRootPath)
        def encoder = Mock(Encoder)
        def cachedArtifact = Mock(CachedArtifact)

        when:
        valueSerializer.write(encoder, cachedArtifact)

        then:
        2 * cachedArtifact.isMissing() >> false
        1 * cachedArtifact.cachedAt >> 42L
        1 * cachedArtifact.getDescriptorHash() >> TestHashCodes.hashCodeFrom(42)
        1 * cachedArtifact.getCachedFile() >> commonRootPath.resolve("file.txt").toFile()

        1 * encoder.writeString("file.txt")
    }

    def "value serializer can expand relative path"() {
        given:
        Serializer<CachedArtifact> valueSerializer = new DefaultModuleArtifactCache.CachedArtifactSerializer(commonRootPath)
        def decoder = Mock(Decoder)
        def fileName = "file.txt"

        when:
        def cachedArtifact = valueSerializer.read(decoder)

        then:
        1 * decoder.readBoolean() >> false
        1 * decoder.readLong() >> 42L
        1 * decoder.readBinary() >> TestHashCodes.hashCodeFrom(42).toByteArray()
        1 * decoder.readString() >> fileName

        cachedArtifact.getCachedFile() == commonRootPath.resolve(fileName).toFile()
    }

    def createEntryInPersistentCache() {
        1 * cacheAccessCoordinator.createCache(persistentCacheFile, _, _) >> persistentIndexedCache
        1 * cacheAccessCoordinator.useCache(_) >> { Supplier factory -> factory.get()}
        def key = new ArtifactAtRepositoryKey("RepoID", Stub(ModuleComponentArtifactIdentifier))
        1 * persistentIndexedCache.getIfPresent(key) >> cachedArtifact
        key
    }
}
