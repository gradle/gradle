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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentUsage
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class   InMemoryCachedModuleComponentRepositoryTest extends Specification {

    def stats = new InMemoryCacheStats()
    def localArtifactsCache = Mock(InMemoryArtifactsCache)
    def remoteArtifactsCache = Mock(InMemoryArtifactsCache)
    def localMetaDataCache = Mock(InMemoryMetaDataCache)
    def remoteMetaDataCache = Mock(InMemoryMetaDataCache)
    def caches = new InMemoryModuleComponentRepositoryCaches(localArtifactsCache, remoteArtifactsCache, localMetaDataCache, remoteMetaDataCache, stats);
    def localDelegate = Mock(ModuleComponentRepositoryAccess)
    def remoteDelegate = Mock(ModuleComponentRepositoryAccess)
    def delegate = Mock(ModuleComponentRepository) {
        getLocalAccess() >> localDelegate
        getRemoteAccess() >> remoteDelegate
    }
    def repo = new InMemoryCachedModuleComponentRepository(caches, delegate)
    def lib = Mock(ModuleComponentIdentifier)
    def selector = newSelector("org", "lib", "1.0")
    def dep = Stub(DependencyMetaData) { getRequested() >> selector }
    def componentRequestMetaData = Mock(ComponentOverrideMetadata)


    def listingResult = Mock(BuildableModuleVersionListingResolveResult)
    def metaDataResult = Mock(BuildableModuleComponentMetaDataResolveResult)

    def "delegates"() {
        when:
        def id = repo.getId()
        def name = repo.getName()

        then:
        id == "x"
        name == "localRepo"
        1 * delegate.getId() >> "x"
        1 * delegate.getName() >> "localRepo"
    }

    def "retrieves and caches module version listings"() {
        when:
        repo.localAccess.listModuleVersions(dep, listingResult)

        then:
        1 * localMetaDataCache.supplyModuleVersions(selector, listingResult) >> false
        1 * localDelegate.listModuleVersions(dep, listingResult)
        1 * localMetaDataCache.newModuleVersions(selector, listingResult)
        0 * _

        when:
        repo.remoteAccess.listModuleVersions(dep, listingResult)

        then:
        1 * remoteMetaDataCache.supplyModuleVersions(selector, listingResult) >> false
        1 * remoteDelegate.listModuleVersions(dep, listingResult)
        1 * remoteMetaDataCache.newModuleVersions(selector, listingResult)
        0 * _
    }

    def "uses module version listings from cache"() {
        when:
        repo.localAccess.listModuleVersions(dep, listingResult)

        then:
        1 * localMetaDataCache.supplyModuleVersions(selector, listingResult) >> true
        0 * _

        when:
        repo.remoteAccess.listModuleVersions(dep, listingResult)

        then:
        1 * remoteMetaDataCache.supplyModuleVersions(selector, listingResult) >> true
        0 * _
    }

    def "retrieves and caches local dependencies"() {
        when:
        repo.localAccess.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)

        then:
        1 * localMetaDataCache.supplyMetaData(lib, metaDataResult) >> false
        1 * localDelegate.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)
        1 * localMetaDataCache.newDependencyResult(lib, metaDataResult)
        0 * _
    }

    def "uses local dependencies from cache"() {
        when:
        repo.localAccess.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)

        then:
        1 * localMetaDataCache.supplyMetaData(lib, metaDataResult) >> true
        0 * _
    }

    def "retrieves and caches dependencies"() {
        when:
        repo.remoteAccess.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)

        then:
        1 * remoteMetaDataCache.supplyMetaData(lib, metaDataResult) >> false
        1 * remoteDelegate.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)
        1 * remoteMetaDataCache.newDependencyResult(lib, metaDataResult)
        0 * _
    }

    def "uses dependencies from cache"() {
        when:
        repo.remoteAccess.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)

        then:
        1 * remoteMetaDataCache.supplyMetaData(lib, metaDataResult) >> true
        0 * _
    }

    def "delegates request for module artifacts by type"() {
        def moduleMetaData = Stub(ModuleComponentResolveMetaData)
        def artifactType = ArtifactType.JAVADOC
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.localAccess.resolveModuleArtifacts(moduleMetaData, artifactType, result)

        then:
        1 * localDelegate.resolveModuleArtifacts(moduleMetaData, artifactType, result)
        0 * _

        when:
        repo.remoteAccess.resolveModuleArtifacts(moduleMetaData, artifactType, result)

        then:
        1 * remoteDelegate.resolveModuleArtifacts(moduleMetaData, artifactType, result)
        0 * _
    }

    def "delegates request for module artifacts for usage"() {
        def moduleMetaData = Stub(ModuleComponentResolveMetaData)
        def componentUsage = Stub(ComponentUsage)
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.localAccess.resolveModuleArtifacts(moduleMetaData, componentUsage, result)

        then:
        1 * localDelegate.resolveModuleArtifacts(moduleMetaData, componentUsage, result)
        0 * _

        when:
        repo.remoteAccess.resolveModuleArtifacts(moduleMetaData, componentUsage, result)

        then:
        1 * remoteDelegate.resolveModuleArtifacts(moduleMetaData, componentUsage, result)
        0 * _
    }

    def "retrieves and caches artifacts"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetaData) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.localAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * localArtifactsCache.supplyArtifact(artifactId, result) >> false
        1 * localDelegate.resolveArtifact(artifact, moduleSource, result)
        1 * localArtifactsCache.newArtifact(artifactId, result)
        0 * _

        when:
        repo.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * remoteArtifactsCache.supplyArtifact(artifactId, result) >> false
        1 * remoteDelegate.resolveArtifact(artifact, moduleSource, result)
        1 * remoteArtifactsCache.newArtifact(artifactId, result)
        0 * _
    }

    def "uses artifacts from cache"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetaData) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.localAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * localArtifactsCache.supplyArtifact(artifactId, result) >> true
        0 * _

        when:
        repo.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * remoteArtifactsCache.supplyArtifact(artifactId, result) >> true
        0 * _
    }
}
