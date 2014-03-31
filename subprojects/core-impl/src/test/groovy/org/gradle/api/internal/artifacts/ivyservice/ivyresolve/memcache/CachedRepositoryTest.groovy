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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionSelectionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalAwareModuleVersionRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class CachedRepositoryTest extends Specification {

    def stats = new DependencyMetadataCacheStats()
    def cache = Mock(DependencyMetadataCache)
    def delegate = Mock(LocalAwareModuleVersionRepository)
    def repo = new CachedRepository(cache, delegate, stats)

    def lib = newSelector("org", "lib", "1.0")
    def dep = Stub(DependencyMetaData) { getRequested() >> lib }
    def listingResult = Mock(BuildableModuleVersionSelectionResolveResult)
    def metaDataResult = Mock(BuildableModuleVersionMetaDataResolveResult)

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
        repo.localListModuleVersions(dep, listingResult)

        then:
        1 * cache.supplyLocalModuleVersions(lib, listingResult) >> false
        1 * delegate.localListModuleVersions(dep, listingResult)
        1 * cache.newLocalModuleVersions(lib, listingResult)
        0 * _

        when:
        repo.listModuleVersions(dep, listingResult)

        then:
        1 * cache.supplyModuleVersions(lib, listingResult) >> false
        1 * delegate.listModuleVersions(dep, listingResult)
        1 * cache.newModuleVersions(lib, listingResult)
        0 * _
    }

    def "uses module version listings from cache"() {
        when:
        repo.localListModuleVersions(dep, listingResult)

        then:
        1 * cache.supplyLocalModuleVersions(lib, listingResult) >> true
        0 * _

        when:
        repo.listModuleVersions(dep, listingResult)

        then:
        1 * cache.supplyModuleVersions(lib, listingResult) >> true
        0 * _
    }

    def "retrieves and caches local dependencies"() {
        when:
        repo.getLocalDependency(dep, metaDataResult)

        then:
        1 * cache.supplyLocalMetaData(lib, metaDataResult) >> false
        1 * delegate.getLocalDependency(dep, metaDataResult)
        1 * cache.newLocalDependencyResult(lib, metaDataResult)
        0 * _
    }

    def "uses local dependencies from cache"() {
        when:
        repo.getLocalDependency(dep, metaDataResult)

        then:
        1 * cache.supplyLocalMetaData(lib, metaDataResult) >> true
        0 * _
    }

    def "retrieves and caches dependencies"() {
        when:
        repo.getDependency(dep, metaDataResult)

        then:
        1 * cache.supplyMetaData(lib, metaDataResult) >> false
        1 * delegate.getDependency(dep, metaDataResult)
        1 * cache.newDependencyResult(lib, metaDataResult)
        0 * _
    }

    def "uses dependencies from cache"() {
        when:
        repo.getDependency(dep, metaDataResult)

        then:
        1 * cache.supplyMetaData(lib, metaDataResult) >> true
        0 * _
    }

    def "delegates request for module artifacts"() {
        def moduleMetaData = Stub(ModuleVersionMetaData)
        def context = Stub(ArtifactResolveContext)
        def result = Mock(BuildableArtifactSetResolveResult)

        when:
        repo.resolveModuleArtifacts(moduleMetaData, context, result)

        then:
        1 * delegate.resolveModuleArtifacts(moduleMetaData, context, result)
        0 * _
    }

    def "retrieves and caches artifacts"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleVersionArtifactIdentifier)
        def artifact = Stub(ModuleVersionArtifactMetaData) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * cache.supplyArtifact(artifactId, result) >> false
        1 * delegate.resolveArtifact(artifact, moduleSource, result)
        1 * cache.newArtifact(artifactId, result)
        0 * _
    }

    def "uses artifacts from cache"() {
        def result = Mock(BuildableArtifactResolveResult)
        def artifactId = Stub(ModuleVersionArtifactIdentifier)
        def artifact = Stub(ModuleVersionArtifactMetaData) {
            getId() >> artifactId
        }
        def moduleSource = Mock(ModuleSource)

        when:
        repo.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * cache.supplyArtifact(artifactId, result) >> true
        0 * _
    }
}
