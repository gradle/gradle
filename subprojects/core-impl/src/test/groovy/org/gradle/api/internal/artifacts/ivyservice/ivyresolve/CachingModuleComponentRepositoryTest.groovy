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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.ivyservice.ArtifactType
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ComponentUsage
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableArtifactSetResolveResult
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import org.gradle.internal.resource.cached.CachedArtifactIndex
import org.gradle.internal.resource.cached.ivy.ArtifactAtRepositoryKey
import org.gradle.util.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Unroll

class CachingModuleComponentRepositoryTest extends Specification {
    def realLocalAccess = Mock(ModuleComponentRepositoryAccess)
    def realRemoteAccess = Mock(ModuleComponentRepositoryAccess)
    def realRepo = Stub(ModuleComponentRepository) {
        getId() >> "repo-id"
        getLocalAccess() >> realLocalAccess
        getRemoteAccess() >> realRemoteAccess
    }
    def moduleResolutionCache = Stub(ModuleVersionsCache)
    def moduleDescriptorCache = Mock(ModuleMetaDataCache)
    def moduleArtifactsCache = Mock(ModuleArtifactsCache)
    def artifactAtRepositoryCache = Mock(CachedArtifactIndex)
    def cachePolicy = Stub(CachePolicy)
    def metadataProcessor = Stub(ModuleMetadataProcessor)
    def repo = new CachingModuleComponentRepository(realRepo, moduleResolutionCache, moduleDescriptorCache, moduleArtifactsCache, artifactAtRepositoryCache,
            cachePolicy, new BuildCommencedTimeProvider(), metadataProcessor)

    @Unroll
    def "artifact last modified date is cached - lastModified = #lastModified"() {
        given:
        def artifactId = Stub(ModuleVersionArtifactIdentifier)
        def artifact = Stub(ModuleVersionArtifactMetaData) {
            getId() >> artifactId
        }

        def file = new File("local")
        def result = Stub(BuildableArtifactResolveResult) {
            getFile() >> file
            getFailure() >> null
        }

        def descriptorHash = 1234G
        def moduleSource = Stub(CachingModuleComponentRepository.CachingModuleSource) {
            getDescriptorHash() >> descriptorHash
        }

        ArtifactAtRepositoryKey atRepositoryKey = new ArtifactAtRepositoryKey("repo-id", artifactId)

        when:
        repo.remoteAccess.resolveArtifact(artifact, moduleSource, result)

        then:
        1 * artifactAtRepositoryCache.store(atRepositoryKey, file, descriptorHash)
        0 * moduleDescriptorCache._

        where:
        lastModified << [new Date(), null]
    }

    def "does not use cache when module version listing can be determined locally"() {
        def dependency = Mock(DependencyMetaData)
        def result = new DefaultBuildableModuleVersionSelectionResolveResult()

        when:
        repo.localAccess.listModuleVersions(dependency, result)

        then:
        realLocalAccess.listModuleVersions(dependency, result) >> {
            result.listed(Mock(ModuleVersionListing))
        }
        0 * _
    }

    def "does not use cache when component metadata can be determined locally"() {
        def dependency = Mock(DependencyMetaData)
        def componentId = Mock(ModuleComponentIdentifier)
        def result = new DefaultBuildableModuleVersionMetaDataResolveResult()

        when:
        repo.localAccess.resolveComponentMetaData(dependency, componentId, result)

        then:
        realLocalAccess.resolveComponentMetaData(dependency, componentId, result) >> {
            result.resolved(Mock(MutableModuleVersionMetaData), Mock(ModuleSource))
        }
        0 * _
    }
    def "does not use cache when artifacts for type can be determined locally"() {
        def component = Mock(ComponentMetaData)
        def source = Mock(ModuleSource)
        def cachingSource = new CachingModuleComponentRepository.CachingModuleSource(BigInteger.ONE, false, source)
        def artifactType = Mock(ArtifactType)
        def result = new DefaultBuildableArtifactSetResolveResult()

        when:
        repo.localAccess.resolveModuleArtifacts(component, artifactType, result)

        then:
        1 * component.getSource() >> cachingSource
        1 * component.withSource(source) >> component
        realLocalAccess.resolveModuleArtifacts(component, artifactType, result) >> {
            result.resolved([Mock(ComponentArtifactMetaData)])
        }
        0 * _
    }

    def "does not use cache when artifacts for usage can be determined locally"() {
        def component = Mock(ComponentMetaData)
        def source = Mock(ModuleSource)
        def cachingSource = new CachingModuleComponentRepository.CachingModuleSource(BigInteger.ONE, false, source)
        def componentUsage = Mock(ComponentUsage)
        def result = new DefaultBuildableArtifactSetResolveResult()

        when:
        repo.localAccess.resolveModuleArtifacts(component, componentUsage, result)

        then:
        1 * component.getSource() >> cachingSource
        1 * component.withSource(source) >> component
        realLocalAccess.resolveModuleArtifacts(component, componentUsage, result) >> {
            result.resolved([Mock(ComponentArtifactMetaData)])
        }
        0 * _
    }
}
