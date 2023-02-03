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

package org.gradle.internal.resource.transfer

import org.gradle.api.Transformer
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinatorStub
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.cached.CachedExternalResource
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class DefaultCacheAwareExternalResourceAccessorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())
    final repository = Mock(ExternalResourceRepository)
    final progressLoggingRepo = Mock(ExternalResourceRepository)
    final index = Mock(CachedExternalResourceIndex)
    final timeProvider = Mock(BuildCommencedTimeProvider)
    final tempFile = tempDir.file("temp-file")
    final cachedFile = tempDir.file("cached-file")
    final temporaryFileProvider = Stub(TemporaryFileProvider) {
        createTemporaryFile(_, _, _) >> tempFile
    }
    final cacheAccessCoordinator = new ArtifactCacheLockingAccessCoordinatorStub()
    final fileRepository = Mock(FileResourceRepository)
    final cachePolicy = new DefaultExternalResourceCachePolicy()
    final ProducerGuard<URI> producerGuard = Stub() {
        guardByKey(_, _) >> { args ->
            def (key, supplier) = args
            supplier.get()
        }
    }
    final cache = new DefaultCacheAwareExternalResourceAccessor(repository, index, timeProvider, temporaryFileProvider, cacheAccessCoordinator, cachePolicy, producerGuard, fileRepository, TestUtil.checksumService)

    def "returns null when the request resource is not cached and does not exist in the remote repository"() {
        def location = new ExternalResourceName("thing")
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def resource = Mock(ExternalResource)
        def localCandidates = Mock(LocallyAvailableResourceCandidates)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == null

        and:
        1 * index.lookup("thing") >> null
        1 * localCandidates.isNone() >> true
        1 * repository.withProgressLogging() >> progressLoggingRepo
        1 * progressLoggingRepo.resource(location) >> resource
        1 * resource.withContentIfPresent(_) >> null
        0 * _._
    }

    def "returns null when the request resource is not cached and there are local candidates but the resource does not exist in the remote repository"() {
        def location = new ExternalResourceName("thing")
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def remoteResource = Mock(ExternalResource)
        def localCandidates = Mock(LocallyAvailableResourceCandidates)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == null

        and:
        1 * index.lookup("thing") >> null
        1 * localCandidates.isNone() >> false
        1 * repository.resource(location, true) >> remoteResource
        1 * remoteResource.metaData >> null
        0 * _._
    }

    def "downloads resource and moves it into the cache when it is not cached"() {
        def location = new ExternalResourceName("thing")
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def remoteResource = Mock(ExternalResource)
        def metaData = Mock(ExternalResourceMetaData)
        def localResource = new DefaultLocallyAvailableResource(cachedFile, TestUtil.checksumService)
        def cachedResource = Stub(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == cachedResource

        and:
        1 * index.lookup("thing") >> null
        1 * localCandidates.isNone() >> true
        1 * repository.withProgressLogging() >> progressLoggingRepo
        1 * progressLoggingRepo.resource(location) >> remoteResource
        _ * remoteResource.name >> "remoteResource"
        1 * remoteResource.withContentIfPresent(_) >> { ExternalResource.ContentAndMetadataAction a ->
            a.execute(new ByteArrayInputStream(), metaData)
        }

        and:
        1 * fileStore.moveIntoCache(tempFile) >> localResource
        1 * index.store("thing", cachedFile, metaData)
        1 * fileRepository.resource(cachedFile, location.uri, metaData) >> cachedResource
        0 * _._
    }

    def "reuses cached resource if it has not expired"() {
        def location = new ExternalResourceName("scheme:thing")
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def metaData = Mock(ExternalResourceMetaData)
        def cachedResource = Stub(CachedExternalResource)
        def resultResource = Mock(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("scheme:thing") >> cachedResource
        _ * timeProvider.currentTime >> 24000L
        _ * cachedResource.cachedAt >> 24000L
        _ * cachedResource.cachedFile >> cachedFile
        _ * cachedResource.externalResourceMetaData >> metaData
        1 * fileRepository.resource(cachedFile, location.uri, metaData) >> resultResource
        0 * _._
    }

    def "will use sha1 from metadata for finding candidates if available"() {
        given:
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def cached = Mock(CachedExternalResource)
        def candidate = tempDir.createFile("candidate-file")
        def sha1 = Hashing.sha1().hashBytes(candidate.bytes)
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def cachedMetaData = Mock(ExternalResourceMetaData)
        def remoteMetaData = Mock(ExternalResourceMetaData)
        def localCandidate = Mock(LocallyAvailableResource)
        def remoteResource = Mock(ExternalResource)
        def location = new ExternalResourceName("thing")
        def localResource = new DefaultLocallyAvailableResource(cachedFile, TestUtil.checksumService)
        def resultResource = Stub(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("thing") >> cached
        timeProvider.currentTime >> 24000L
        cached.cachedAt >> 23999L
        cached.externalResourceMetaData >> cachedMetaData
        1 * repository.resource(location, true) >> remoteResource
        1 * remoteResource.metaData >> remoteMetaData
        localCandidates.none >> false
        remoteMetaData.sha1 >> sha1
        remoteMetaData.etag >> null
        remoteMetaData.lastModified >> null
        cachedMetaData.etag >> null
        cachedMetaData.lastModified >> null
        1 * localCandidates.findByHashValue(sha1) >> localCandidate
        localCandidate.file >> candidate
        cached.cachedFile >> cachedFile
        0 * _._

        and:
        1 * fileStore.moveIntoCache(tempFile) >> localResource
        1 * index.store("thing", cachedFile, remoteMetaData)
        1 * fileRepository.resource(cachedFile, location.uri, remoteMetaData) >> resultResource
        0 * _._
    }

    def "reuses cached resource if ETag matches"() {
        given:
        def etag = "686897696a7c876b7e"
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def candidate = tempDir.createFile("candidate-file")
        def sha1 = Hashing.sha1().hashFile(candidate)
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def cachedMetaData = Stub(ExternalResourceMetaData) {
            getEtag() >> etag
            getContentLength() >> 42
        }
        def remoteMetaData = Stub(ExternalResourceMetaData) {
            getEtag() >> etag
            getSha1() >> sha1
        }
        def remoteResource = Stub(ExternalResource) {
            getMetaData() >> remoteMetaData
        }
        def location = new ExternalResourceName("thing")
        def resultResource = Stub(LocallyAvailableExternalResource) {
            getFile() >> cachedFile
        }

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("thing") >> Stub(CachedExternalResource) {
            getCachedAt() >> 23999L
            getExternalResourceMetaData() >> cachedMetaData
            getCachedFile() >> cachedFile
        }
        timeProvider.currentTime >> 24000L
        1 * repository.resource(location, true) >> remoteResource
        1 * index.store("thing", cachedFile, cachedMetaData)
        1 * fileRepository.resource(cachedFile, location.uri, cachedMetaData) >> resultResource
        0 * _._
    }

    def "will download sha1 for finding candidates if not available in meta-data"() {
        given:
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def candidate = tempDir.createFile("candidate-file")
        def sha1 = Hashing.sha1().hashBytes(candidate.bytes)
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def cachedMetaData = Mock(ExternalResourceMetaData)
        def remoteMetaData = Mock(ExternalResourceMetaData)
        def localCandidate = Mock(LocallyAvailableResource)
        def remoteResource = Mock(ExternalResource)
        def remoteSha1 = Mock(ExternalResource)
        def location = new ExternalResourceName("thing")
        def localResource = new DefaultLocallyAvailableResource(cachedFile, TestUtil.checksumService)
        def resultResource = Stub(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("thing") >> null
        1 * repository.resource(location, true) >> remoteResource
        1 * remoteResource.metaData >> remoteMetaData
        localCandidates.none >> false
        remoteMetaData.sha1 >> null
        remoteMetaData.etag >> null
        remoteMetaData.lastModified >> null
        cachedMetaData.etag >> null
        cachedMetaData.lastModified >> null
        1 * repository.resource(new ExternalResourceName("thing.sha1"), true) >> remoteSha1
        1 * remoteSha1.withContentIfPresent(_) >> { ExternalResource.ContentAction action ->
            ExternalResourceReadResult.of(1, action.execute(new ByteArrayInputStream(sha1.toString().getBytes("us-ascii"))))
        }
        1 * localCandidates.findByHashValue(sha1) >> localCandidate
        localCandidate.file >> candidate
        0 * _._

        and:
        1 * fileStore.moveIntoCache(tempFile) >> localResource
        1 * index.store("thing", cachedFile, remoteMetaData)
        1 * fileRepository.resource(cachedFile, location.uri, remoteMetaData) >> resultResource
        0 * _._
    }

    def "downloads resource directly when no remote sha1 available"() {
        given:
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def cachedMetaData = Mock(ExternalResourceMetaData)
        def remoteMetaData = Mock(ExternalResourceMetaData)
        def remoteResource = Mock(ExternalResource)
        def remoteSha1 = Mock(ExternalResource)
        def location = new ExternalResourceName("thing")
        def localResource = new DefaultLocallyAvailableResource(cachedFile, TestUtil.checksumService)
        def resultResource = Stub(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("thing") >> null
        1 * repository.resource(location, true) >> remoteResource
        1 * remoteResource.metaData >> remoteMetaData
        localCandidates.none >> false
        remoteMetaData.sha1 >> null
        remoteMetaData.etag >> null
        remoteMetaData.lastModified >> null
        cachedMetaData.etag >> null
        cachedMetaData.lastModified >> null
        1 * repository.resource(new ExternalResourceName("thing.sha1"), true) >> remoteSha1
        1 * remoteSha1.withContentIfPresent(_) >> null
        1 * repository.withProgressLogging() >> progressLoggingRepo
        1 * progressLoggingRepo.resource(location, true) >> remoteResource
        1 * remoteResource.withContentIfPresent(_) >> { ExternalResource.ContentAndMetadataAction a ->
            a.execute(new ByteArrayInputStream(), remoteMetaData)
        }
        0 * _._

        and:
        1 * fileStore.moveIntoCache(tempFile) >> localResource
        1 * index.store("thing", cachedFile, remoteMetaData)
        1 * fileRepository.resource(cachedFile, location.uri, remoteMetaData) >> resultResource
        0 * _._
    }

    @Issue('https://github.com/gradle/gradle/issues/4893')
    def "downloads resource directly when remote sha1 is not readable"() {
        given:
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def cachedMetaData = Mock(ExternalResourceMetaData)
        def remoteMetaData = Mock(ExternalResourceMetaData)
        def remoteResource = Mock(ExternalResource)
        def remoteSha1 = Mock(ExternalResource)
        def location = new ExternalResourceName("thing")
        def localResource = new DefaultLocallyAvailableResource(cachedFile, TestUtil.checksumService)
        def resultResource = Stub(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("thing") >> null
        1 * repository.resource(location, true) >> remoteResource
        1 * remoteResource.metaData >> remoteMetaData
        localCandidates.none >> false
        remoteMetaData.sha1 >> null
        remoteMetaData.etag >> null
        remoteMetaData.lastModified >> null
        cachedMetaData.etag >> null
        cachedMetaData.lastModified >> null
        1 * repository.resource(new ExternalResourceName("thing.sha1"), true) >> remoteSha1
        1 * remoteSha1.withContentIfPresent(_) >> { Transformer t ->
            ExternalResourceReadResult.of(1, t.transform(new ByteArrayInputStream("proc".bytes)))
        }
        1 * repository.withProgressLogging() >> progressLoggingRepo
        1 * progressLoggingRepo.resource(location, true) >> remoteResource
        1 * remoteResource.withContentIfPresent(_) >> { ExternalResource.ContentAndMetadataAction a ->
            a.execute(new ByteArrayInputStream(), remoteMetaData)
        }
        0 * _._

        and:
        1 * fileStore.moveIntoCache(tempFile) >> localResource
        1 * index.store("thing", cachedFile, remoteMetaData)
        1 * fileRepository.resource(cachedFile, location.uri, remoteMetaData) >> resultResource
        0 * _._
    }

    def "downloads resource directly when local candidate cannot be copied"() {
        given:
        def localCandidates = Mock(LocallyAvailableResourceCandidates)
        def cached = Mock(CachedExternalResource)
        def candidate = tempDir.createFile("candidate-file")
        def sha1 = Hashing.sha1().hashBytes(candidate.bytes)
        candidate << "some extra stuff"
        def fileStore = Mock(CacheAwareExternalResourceAccessor.ResourceFileStore)
        def cachedMetaData = Mock(ExternalResourceMetaData)
        def remoteMetaData = Mock(ExternalResourceMetaData)
        def localCandidate = Mock(LocallyAvailableResource)
        def location = new ExternalResourceName("thing")
        def remoteResource = Mock(ExternalResource)
        def localResource = new DefaultLocallyAvailableResource(cachedFile, TestUtil.checksumService)
        def resultResource = Stub(LocallyAvailableExternalResource)

        when:
        def result = cache.getResource(location, null, fileStore, localCandidates)

        then:
        result == resultResource

        and:
        1 * index.lookup("thing") >> cached
        timeProvider.currentTime >> 24000L
        cached.cachedAt >> 23999L
        cached.externalResourceMetaData >> cachedMetaData
        1 * repository.resource(location, true) >> remoteResource
        1 * remoteResource.metaData >> remoteMetaData
        localCandidates.none >> false
        remoteMetaData.sha1 >> sha1
        remoteMetaData.etag >> null
        remoteMetaData.lastModified >> null
        cachedMetaData.etag >> null
        cachedMetaData.lastModified >> null
        1 * localCandidates.findByHashValue(sha1) >> localCandidate
        localCandidate.file >> candidate
        cached.cachedFile >> cachedFile
        1 * repository.withProgressLogging() >> progressLoggingRepo
        1 * progressLoggingRepo.resource(location, true) >> remoteResource
        1 * remoteResource.withContentIfPresent(_) >> { ExternalResource.ContentAndMetadataAction a ->
            a.execute(new ByteArrayInputStream(), remoteMetaData)
        }
        0 * _._

        and:
        1 * fileStore.moveIntoCache(tempFile) >> localResource
        1 * index.store("thing", cachedFile, remoteMetaData)
        1 * fileRepository.resource(cachedFile, location.uri, remoteMetaData) >> resultResource
        0 * _._
    }
}
