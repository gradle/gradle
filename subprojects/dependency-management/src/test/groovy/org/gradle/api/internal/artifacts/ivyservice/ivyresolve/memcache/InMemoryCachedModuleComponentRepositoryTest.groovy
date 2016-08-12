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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class InMemoryCachedModuleComponentRepositoryTest extends Specification {
    def localArtifactsCache = Mock(InMemoryArtifactsCache)
    def remoteArtifactsCache = Mock(InMemoryArtifactsCache)
    def localMetaDataCache = Mock(InMemoryMetaDataCache)
    def remoteMetaDataCache = Mock(InMemoryMetaDataCache)
    def caches = new InMemoryModuleComponentRepositoryCaches(localArtifactsCache, remoteArtifactsCache, localMetaDataCache, remoteMetaDataCache);
    def localDelegate = Mock(ModuleComponentRepositoryAccess)
    def remoteDelegate = Mock(ModuleComponentRepositoryAccess)
    def delegate = Mock(ModuleComponentRepository) {
        getLocalAccess() >> localDelegate
        getRemoteAccess() >> remoteDelegate
    }
    def repo = new InMemoryCachedModuleComponentRepository(caches, delegate)
    def lib = Mock(ModuleComponentIdentifier)
    def selector = newSelector("org", "lib", "1.0")
    def dep = Stub(DependencyMetadata) { getRequested() >> selector }
    def componentRequestMetaData = Mock(ComponentOverrideMetadata)


    def listingResult = Mock(BuildableModuleVersionListingResolveResult)
    def metaDataResult = Mock(BuildableModuleComponentMetaDataResolveResult)

    def "delegates repo properties"() {
        when:
        def id = repo.getId()
        def name = repo.getName()

        then:
        id == "x"
        name == "localRepo"
        1 * delegate.getId() >> "x"
        1 * delegate.getName() >> "localRepo"
    }

    def "retrieves and caches local module version listings"() {
        when:
        repo.localAccess.listModuleVersions(dep, listingResult)

        then:
        1 * localMetaDataCache.supplyModuleVersions(selector, listingResult) >> false
        1 * localDelegate.listModuleVersions(dep, listingResult)
        1 * localMetaDataCache.newModuleVersions(selector, listingResult)
        0 * _
    }

    def "retrieves and caches remote module version listings"() {
        when:
        repo.remoteAccess.listModuleVersions(dep, listingResult)

        then:
        1 * remoteMetaDataCache.supplyModuleVersions(selector, listingResult) >> false
        1 * remoteDelegate.listModuleVersions(dep, listingResult)
        1 * remoteMetaDataCache.newModuleVersions(selector, listingResult)
        0 * _
    }

    def "uses local module version listings from cache"() {
        when:
        repo.localAccess.listModuleVersions(dep, listingResult)

        then:
        1 * localMetaDataCache.supplyModuleVersions(selector, listingResult) >> true
        0 * _
    }

    def "uses remote module version listings from cache"() {
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

    def "retrieves and caches remote dependencies"() {
        when:
        repo.remoteAccess.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)

        then:
        1 * remoteMetaDataCache.supplyMetaData(lib, metaDataResult) >> false
        1 * remoteDelegate.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)
        1 * remoteMetaDataCache.newDependencyResult(lib, metaDataResult)
        0 * _
    }

    def "uses remote dependencies from cache"() {
        when:
        repo.remoteAccess.resolveComponentMetaData(lib, componentRequestMetaData, metaDataResult)

        then:
        1 * remoteMetaDataCache.supplyMetaData(lib, metaDataResult) >> true
        0 * _
    }

    def "requests and caches local module artifacts by type"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def artifactType = ArtifactType.JAVADOC
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.localAccess.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * localArtifactsCache.supplyArtifacts(componentId, artifactType, result) >> false
        1 * localDelegate.resolveArtifactsWithType(component, artifactType, result)
        1 * localArtifactsCache.newArtifacts(componentId, artifactType, result)
        0 * _
    }

    def "requests and caches remote module artifacts by type"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def artifactType = ArtifactType.JAVADOC
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.remoteAccess.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * remoteArtifactsCache.supplyArtifacts(componentId, artifactType, result) >> false
        1 * remoteDelegate.resolveArtifactsWithType(component, artifactType, result)
        1 * remoteArtifactsCache.newArtifacts(componentId, artifactType, result)
        0 * _
    }

    def "uses cached local module artifacts by type"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def artifactType = ArtifactType.JAVADOC
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.localAccess.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * localArtifactsCache.supplyArtifacts(componentId, artifactType, result) >> true
        0 * _
    }

    def "uses cached remote module artifacts by type"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def artifactType = ArtifactType.JAVADOC
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.remoteAccess.resolveArtifactsWithType(component, artifactType, result)

        then:
        1 * remoteArtifactsCache.supplyArtifacts(componentId, artifactType, result) >> true
        0 * _
    }

    def "requests and caches local module artifacts"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def result = Mock(BuildableComponentArtifactsResolveResult)

        when:
        repo.localAccess.resolveArtifacts(component, result)

        then:
        1 * localArtifactsCache.supplyArtifacts(componentId, result) >> false
        1 * localDelegate.resolveArtifacts(component, result)
        1 * localArtifactsCache.newArtifacts(componentId, result)
        0 * _
    }

    def "requests and caches remote module artifacts"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def result = Mock(BuildableComponentArtifactsResolveResult)

        when:
        repo.remoteAccess.resolveArtifacts(component, result)

        then:
        1 * remoteArtifactsCache.supplyArtifacts(componentId, result) >> false
        1 * remoteDelegate.resolveArtifacts(component, result)
        1 * remoteArtifactsCache.newArtifacts(componentId, result)
        0 * _
    }

    def "uses local module artifacts from cache"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def result = Mock(BuildableComponentArtifactsResolveResult)

        when:
        repo.localAccess.resolveArtifacts(component, result)

        then:
        1 * localArtifactsCache.supplyArtifacts(componentId, result) >> true
        0 * _
    }

    def "uses remote module artifacts from cache"() {
        def componentId = Stub(ComponentIdentifier)
        def component = Stub(ComponentResolveMetadata) {
            getComponentId() >> componentId
        }
        def result = Mock(BuildableComponentArtifactsResolveResult)

        when:
        repo.remoteAccess.resolveArtifacts(component, result)

        then:
        1 * remoteArtifactsCache.supplyArtifacts(componentId, result) >> true
        0 * _
    }

    def "retrieves and caches local artifacts"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetadata) {
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
    }

    def "retrieves and caches remote artifacts"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetadata) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * remoteArtifactsCache.supplyArtifact(artifactId, result) >> false
        1 * remoteDelegate.resolveArtifact(artifact, moduleSource, result)
        1 * remoteArtifactsCache.newArtifact(artifactId, result)
        0 * _
    }

    def "uses local artifacts from cache"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetadata) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.localAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * localArtifactsCache.supplyArtifact(artifactId, result) >> true
        0 * _
    }

    def "uses remote artifacts from cache"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetadata) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * remoteArtifactsCache.supplyArtifact(artifactId, result) >> true
        0 * _
    }
}
