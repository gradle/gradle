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
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry
import org.gradle.api.internal.artifacts.ivyservice.modulecache.AbstractModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ArtifactAtRepositoryKey
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.hash.Hashing
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleVersionListingResolveResult
import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Specification

import java.time.Duration

class CachingModuleComponentRepositoryTest extends Specification {
    def realLocalAccess = Mock(ModuleComponentRepositoryAccess)
    def realRemoteAccess = Mock(ModuleComponentRepositoryAccess)
    def realRepo = Stub(ModuleComponentRepository) {
        getId() >> "repo-id"
        getLocalAccess() >> realLocalAccess
        getRemoteAccess() >> realRemoteAccess
    }
    def moduleResolutionCache = Stub(AbstractModuleVersionsCache)
    def moduleDescriptorCache = Mock(AbstractModuleMetadataCache)
    def moduleArtifactsCache = Mock(AbstractArtifactsCache)
    def artifactAtRepositoryCache = Mock(ModuleArtifactCache)
    def cachePolicy = Stub(CachePolicy)
    def metadataProcessor = Stub(ComponentMetadataProcessor)
    def listener = Stub(ChangingValueDependencyResolutionListener)
    def caches = new ModuleRepositoryCaches(moduleResolutionCache, moduleDescriptorCache, moduleArtifactsCache, artifactAtRepositoryCache)
    def resolveStateFactory = DependencyManagementTestUtil.modelGraphResolveFactory()
    def repo = new CachingModuleComponentRepository(realRepo, caches, resolveStateFactory, cachePolicy, Stub(BuildCommencedTimeProvider), metadataProcessor, listener)

    def "artifact last modified date is cached - lastModified = #lastModified"() {
        given:
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifact = Stub(ModuleComponentArtifactMetadata) {
            getId() >> artifactId
        }

        def file = new File("local")
        def result = Stub(BuildableArtifactFileResolveResult) {
            getFile() >> file
            getFailure() >> null
        }

        def descriptorHash = Hashing.sha1().hashString("Hello")
        def moduleSource = Stub(ModuleDescriptorHashModuleSource) {
            getDescriptorHash() >> descriptorHash
        }

        ArtifactAtRepositoryKey atRepositoryKey = new ArtifactAtRepositoryKey("repo-id", artifactId)

        when:
        repo.remoteAccess.resolveArtifact(artifact, ImmutableModuleSources.of(moduleSource), result)

        then:
        1 * artifactAtRepositoryCache.store(atRepositoryKey, file, descriptorHash)
        0 * moduleDescriptorCache._

        where:
        lastModified << [new Date(), null]
    }

    def "does not use cache when module version listing can be determined locally"() {
        def dependency = Mock(ModuleDependencyMetadata)
        def result = new DefaultBuildableModuleVersionListingResolveResult()

        when:
        repo.localAccess.listModuleVersions(dependency, result)

        then:
        realLocalAccess.listModuleVersions(dependency, result) >> {
            result.listed(['a', 'b', 'c'])
        }
        0 * _
    }

    def "does not use cache when component metadata can be determined locally"() {
        def componentId = Mock(ModuleComponentIdentifier)
        def prescribedMetaData = Mock(ComponentOverrideMetadata)
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()

        when:
        repo.localAccess.resolveComponentMetaData(componentId, prescribedMetaData, result)

        then:
        1 * realLocalAccess.resolveComponentMetaData(componentId, prescribedMetaData, _) >> { id, m, r ->
            r.resolved(Stub(ModuleComponentResolveMetadata))
        }
        0 * _
    }

    def "does not use cache when artifacts for type can be determined locally"() {
        def component = Mock(ComponentResolveMetadata)
        def artifactType = ArtifactType.JAVADOC
        def result = new DefaultBuildableArtifactSetResolveResult()

        when:
        repo.localAccess.resolveArtifactsWithType(component, artifactType, result)

        then:
        realLocalAccess.resolveArtifactsWithType(component, artifactType, result) >> {
            result.resolved([Mock(ComponentArtifactMetadata)])
        }
        0 * _
    }

    def "delegates estimates for fetching metadata to remote when not found in cache (remote says #remoteAnswer)"() {
        def module = Mock(ModuleComponentIdentifier)
        def localAccess = repo.localAccess

        when:
        def cost = localAccess.estimateMetadataFetchingCost(module)

        then:
        1 * realRemoteAccess.estimateMetadataFetchingCost(module) >> remoteAnswer
        cost == remoteAnswer

        where:
        remoteAnswer << MetadataFetchingCost.values()
    }

    def "estimates cost for missing metadata is correct (remote says #remoteAnswer, must refresh = #mustRefreshMissingModule)"() {
        def module = Mock(ModuleComponentIdentifier)
        def localAccess = repo.localAccess
        realRemoteAccess.estimateMetadataFetchingCost(module) >> remoteAnswer
        cachePolicy.missingModuleExpiry(_, _) >> Stub(Expiry) {
            isMustCheck() >> mustRefreshMissingModule
        }
        moduleDescriptorCache.getCachedModuleDescriptor(_, module) >> Stub(ModuleMetadataCache.CachedMetadata) {
            isMissing() >> true
            getAge() >> Duration.ofMillis(100)
        }

        when:
        def cost = localAccess.estimateMetadataFetchingCost(module)

        then:
        cost == expected

        where:
        mustRefreshMissingModule | remoteAnswer                   | expected
        false                    | MetadataFetchingCost.CHEAP     | MetadataFetchingCost.CHEAP
        false                    | MetadataFetchingCost.FAST      | MetadataFetchingCost.CHEAP
        false                    | MetadataFetchingCost.EXPENSIVE | MetadataFetchingCost.CHEAP
        true                     | MetadataFetchingCost.CHEAP     | MetadataFetchingCost.CHEAP
        true                     | MetadataFetchingCost.FAST      | MetadataFetchingCost.FAST
        true                     | MetadataFetchingCost.EXPENSIVE | MetadataFetchingCost.EXPENSIVE
    }

    def "estimates cost for changing metadata is correct (remote says #remoteAnswer, must refresh = #mustRefreshChangingModule)"() {
        def module = Mock(ModuleComponentIdentifier)
        def localAccess = repo.localAccess
        realRemoteAccess.estimateMetadataFetchingCost(module) >> remoteAnswer
        cachePolicy.changingModuleExpiry(_, _, _) >> Stub(Expiry) {
            isMustCheck() >> mustRefreshChangingModule
        }
        moduleDescriptorCache.getCachedModuleDescriptor(_, module) >> Stub(ModuleMetadataCache.CachedMetadata) {
            getProcessedMetadata(_) >> Stub(ModuleComponentGraphResolveState) {
                getMetadata() >> Stub(ModuleComponentResolveMetadata) {
                    isChanging() >> true
                }
            }
            getAge() >> Duration.ofMillis(100)
        }

        when:
        def cost = localAccess.estimateMetadataFetchingCost(module)

        then:
        cost == expected

        where:
        mustRefreshChangingModule | remoteAnswer                   | expected
        false                     | MetadataFetchingCost.CHEAP     | MetadataFetchingCost.FAST
        false                     | MetadataFetchingCost.FAST      | MetadataFetchingCost.FAST
        false                     | MetadataFetchingCost.EXPENSIVE | MetadataFetchingCost.FAST
        true                      | MetadataFetchingCost.CHEAP     | MetadataFetchingCost.CHEAP
        true                      | MetadataFetchingCost.FAST      | MetadataFetchingCost.FAST
        true                      | MetadataFetchingCost.EXPENSIVE | MetadataFetchingCost.EXPENSIVE
    }

    def "estimates cost for stable metadata is correct (remote says #remoteAnswer, must refresh = #mustRefreshModule)"() {
        def module = Mock(ModuleComponentIdentifier)
        def localAccess = repo.localAccess
        realRemoteAccess.estimateMetadataFetchingCost(module) >> remoteAnswer
        cachePolicy.moduleExpiry(_, _, _) >> Stub(Expiry) {
            isMustCheck() >> mustRefreshModule
        }
        moduleDescriptorCache.getCachedModuleDescriptor(_, module) >> Stub(ModuleMetadataCache.CachedMetadata) {
            getProcessedMetadata(_) >> Stub(ModuleComponentGraphResolveState)
            getAge() >> Duration.ofMillis(100)
        }

        when:
        def cost = localAccess.estimateMetadataFetchingCost(module)

        then:
        cost == expected

        where:
        mustRefreshModule | remoteAnswer                   | expected
        false             | MetadataFetchingCost.CHEAP     | MetadataFetchingCost.FAST
        false             | MetadataFetchingCost.FAST      | MetadataFetchingCost.FAST
        false             | MetadataFetchingCost.EXPENSIVE | MetadataFetchingCost.FAST
        true              | MetadataFetchingCost.CHEAP     | MetadataFetchingCost.CHEAP
        true              | MetadataFetchingCost.FAST      | MetadataFetchingCost.FAST
        true              | MetadataFetchingCost.EXPENSIVE | MetadataFetchingCost.EXPENSIVE
    }
}
