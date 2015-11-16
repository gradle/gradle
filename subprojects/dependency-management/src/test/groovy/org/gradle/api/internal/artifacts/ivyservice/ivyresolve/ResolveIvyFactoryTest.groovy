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
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryCachedRepositoryFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData
import org.gradle.internal.resource.cached.CachedArtifactIndex
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.internal.resource.transport.ExternalResourceRepository
import org.gradle.util.BuildCommencedTimeProvider
import spock.lang.Specification

class ResolveIvyFactoryTest extends Specification {
    ResolveIvyFactory resolveIvyFactory
    ModuleVersionsCache moduleVersionsCache
    ModuleMetaDataCache moduleMetaDataCache
    ModuleArtifactsCache moduleArtifactsCache
    CachedArtifactIndex cachedArtifactIndex
    CacheLockingManager cacheLockingManager
    StartParameterResolutionOverride startParameterResolutionOverride
    BuildCommencedTimeProvider buildCommencedTimeProvider
    InMemoryCachedRepositoryFactory inMemoryCachedRepositoryFactory
    VersionSelectorScheme versionSelectorScheme
    VersionComparator versionComparator

    def setup() {
        moduleVersionsCache = Mock(ModuleVersionsCache)
        moduleMetaDataCache = Mock(ModuleMetaDataCache)
        moduleArtifactsCache = Mock(ModuleArtifactsCache)
        cachedArtifactIndex = Mock(CachedArtifactIndex)
        cacheLockingManager = Mock(CacheLockingManager)
        startParameterResolutionOverride = Mock(StartParameterResolutionOverride) {
            _ * overrideModuleVersionRepository(_) >> { ModuleComponentRepository repository -> repository }
        }
        buildCommencedTimeProvider = Mock(BuildCommencedTimeProvider)
        inMemoryCachedRepositoryFactory = Mock(InMemoryCachedRepositoryFactory) {
            _ * cached(_) >> { ModuleComponentRepository repository -> repository }
        }
        versionSelectorScheme = Mock(VersionSelectorScheme)
        versionComparator = Mock(VersionComparator)

        resolveIvyFactory = new ResolveIvyFactory(moduleVersionsCache, moduleMetaDataCache, moduleArtifactsCache,
              cachedArtifactIndex, cacheLockingManager, startParameterResolutionOverride, buildCommencedTimeProvider,
              inMemoryCachedRepositoryFactory, versionSelectorScheme, versionComparator)
    }

    def "returns an empty resolver when no repositories are configured" () {
        when:
        def resolver = resolveIvyFactory.create(Stub(ResolutionStrategyInternal), Collections.emptyList(), Stub(ComponentMetadataProcessor))

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
        def resolver = resolveIvyFactory.create(resolutionStrategy, repositories, Stub(ComponentMetadataProcessor))

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
        VersionLister versionLister = Stub()
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetaData> locallyAvailableResourceFinder = Stub()
        FileStore<ModuleComponentArtifactMetaData> fileStore = Stub()

        return Spy(ExternalResourceResolver,
            constructorArgs: [
                    "Spy Resolver",
                    false,
                    externalResourceRepository,
                    cacheAwareExternalResourceAccessor,
                    versionLister,
                    locallyAvailableResourceFinder,
                    fileStore
            ]
        ) {
            getLocalAccess() >> Stub(ModuleComponentRepositoryAccess)
            getRemoteAccess() >> Stub(ModuleComponentRepositoryAccess)
            isM2compatible() >> true
        }
    }
}
