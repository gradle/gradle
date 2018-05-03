/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.Lists
import org.gradle.api.artifacts.ComponentMetadataVersionLister
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.ModuleVersionsCache
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.util.BuildCommencedTimeProvider
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject
import org.gradle.internal.Factory

class ResolveIvyFactoryTest extends Specification {
    @Subject ResolveIvyFactory resolveIvyFactory
    ModuleVersionsCache moduleVersionsCache
    ModuleMetadataCache moduleMetaDataCache
    ModuleArtifactsCache moduleArtifactsCache
    ModuleArtifactCache cachedArtifactIndex
    ModuleRepositoryCacheProvider cacheProvider
    StartParameterResolutionOverride startParameterResolutionOverride
    BuildCommencedTimeProvider buildCommencedTimeProvider
    VersionSelectorScheme versionSelectorScheme
    VersionComparator versionComparator
    ImmutableModuleIdentifierFactory moduleIdentifierFactory
    RepositoryBlacklister repositoryBlacklister
    VersionParser versionParser

    def setup() {
        moduleVersionsCache = Mock(ModuleVersionsCache)
        moduleMetaDataCache = Mock(ModuleMetadataCache)
        moduleArtifactsCache = Mock(ModuleArtifactsCache)
        cachedArtifactIndex = Mock(ModuleArtifactCache)
        def caches = new ModuleRepositoryCaches(moduleVersionsCache, moduleMetaDataCache, moduleArtifactsCache, cachedArtifactIndex)
        cacheProvider = new ModuleRepositoryCacheProvider(caches, caches)
        startParameterResolutionOverride = Mock(StartParameterResolutionOverride) {
            _ * overrideModuleVersionRepository(_) >> { ModuleComponentRepository repository -> repository }
        }
        buildCommencedTimeProvider = Mock(BuildCommencedTimeProvider)
        moduleIdentifierFactory = Mock(ImmutableModuleIdentifierFactory)
        versionSelectorScheme = Mock(VersionSelectorScheme)
        versionComparator = Mock(VersionComparator)
        repositoryBlacklister = Mock(RepositoryBlacklister)
        versionParser = new VersionParser()

        resolveIvyFactory = new ResolveIvyFactory(cacheProvider, startParameterResolutionOverride, buildCommencedTimeProvider,
            versionSelectorScheme, versionComparator, moduleIdentifierFactory, repositoryBlacklister, versionParser)
    }

    def "returns an empty resolver when no repositories are configured" () {
        when:
        def resolver = resolveIvyFactory.create(Stub(ResolutionStrategyInternal), Collections.emptyList(), Stub(ComponentMetadataProcessor), ImmutableAttributes.EMPTY, Stub(AttributesSchemaInternal), TestUtil.attributesFactory())

        then:
        resolver instanceof NoRepositoriesResolver
    }

    def "sets parent resolver with different selection rules when repository is external" () {
        def componentSelectionRules = Stub(ComponentSelectionRulesInternal)

        def resolutionStrategy = Stub(ResolutionStrategyInternal) {
            getComponentSelection() >> componentSelectionRules
        }

        def spyResolver = externalResourceResolverSpy()
        def repositories = Lists.newArrayList(Stub(ResolutionAwareRepository) {
            createResolver() >> spyResolver
        })

        when:
        def resolver = resolveIvyFactory.create(resolutionStrategy, repositories, Stub(ComponentMetadataProcessor), ImmutableAttributes.EMPTY, Stub(AttributesSchemaInternal), TestUtil.attributesFactory())

        then:
        assert resolver instanceof UserResolverChain
        resolver.componentSelectionRules == componentSelectionRules

        1 * spyResolver.setComponentResolvers(_) >> { ComponentResolvers parentResolver ->
            assert parentResolver instanceof ResolveIvyFactory.ParentModuleLookupResolver
            // Validate that the parent repository chain selection rules are different and empty
            def parentComponentSelectionRules = parentResolver.delegate.componentSelectionRules
            assert parentComponentSelectionRules != componentSelectionRules
            assert parentComponentSelectionRules.rules.empty

        }
    }

    def externalResourceResolverSpy() {
        ExternalResourceRepository externalResourceRepository = Stub()
        CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor = Stub()
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder = Stub()
        FileStore<ModuleComponentArtifactMetadata> artifactFileStore = Stub()
        ImmutableMetadataSources metadataSources = Stub()
        MetadataArtifactProvider metadataArtifactProvider = Stub()
        Factory<ComponentMetadataVersionLister> versionListerFactory = Stub()

        return Spy(ExternalResourceResolver,
            constructorArgs: [
                "Spy Resolver",
                false,
                externalResourceRepository,
                cacheAwareExternalResourceAccessor,
                locallyAvailableResourceFinder,
                artifactFileStore,
                moduleIdentifierFactory,
                metadataSources,
                metadataArtifactProvider,
                versionListerFactory
            ]
        ) {
            appendId(_) >> { }
            getLocalAccess() >> Stub(ModuleComponentRepositoryAccess)
            getRemoteAccess() >> Stub(ModuleComponentRepositoryAccess)
            isM2compatible() >> true
        }
    }
}
