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
import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride
import org.gradle.api.internal.artifacts.ivyservice.modulecache.AbstractModuleMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.descriptor.UrlRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Specification

/**
 * Tests {@link ExternalModuleComponentResolverFactory}
 */
class ExternalModuleComponentResolverFactoryTest extends Specification {

    def newFactory() {
        def caches = new ModuleRepositoryCaches(
            Mock(AbstractModuleVersionsCache),
            Mock(AbstractModuleMetadataCache),
            Mock(AbstractArtifactsCache),
            Mock(ModuleArtifactCache)
        )
        ModuleRepositoryCacheProvider cacheProvider = new ModuleRepositoryCacheProvider(caches, caches)
        StartParameterResolutionOverride startParameterResolutionOverride = Mock(StartParameterResolutionOverride) {
            _ * overrideModuleVersionRepository(_) >> { ModuleComponentRepository repository -> repository }
            _ * dependencyVerificationOverride(_, _, _, _, _, _, _) >> DependencyVerificationOverride.NO_VERIFICATION
        }

        def resolveStateFactory = DependencyManagementTestUtil.modelGraphResolveFactory()
        def dependencyVerificationOverride = startParameterResolutionOverride.dependencyVerificationOverride(
            Mock(BuildOperationExecutor),
            TestUtil.checksumService,
            Mock(SignatureVerificationServiceFactory),
            new DocumentationRegistry(),
            Mock(BuildCommencedTimeProvider),
            () -> Mock(GradleProperties),
            Stub(FileResourceListener)
        )

        return new ExternalModuleComponentResolverFactory(
            cacheProvider,
            startParameterResolutionOverride,
            dependencyVerificationOverride,
            Mock(BuildCommencedTimeProvider),
            Mock(VersionComparator),
            Mock(ImmutableModuleIdentifierFactory),
            Mock(RepositoryDisabler),
            new VersionParser(),
            Mock(ListenerManager),
            resolveStateFactory,
            Stub(CalculatedValueContainerFactory),
            AttributeTestUtil.attributesFactory(),
            AttributeTestUtil.services(),
            Stub(ComponentMetadataSupplierRuleExecutor)
        )
    }

    def "returns an empty resolver when no repositories are configured"() {
        when:
        def resolver = newFactory().createResolvers(Collections.emptyList(), Stub(ComponentMetadataProcessorFactory), Stub(ComponentSelectionRulesInternal), false, Mock(CacheExpirationControl), ImmutableAttributes.EMPTY, ImmutableAttributesSchema.EMPTY)

        then:
        resolver instanceof NoRepositoriesResolver
    }

    def "sets parent resolver with different selection rules when repository is external"() {
        def spyResolver = externalResourceResolverSpy()
        def repositories = Lists.newArrayList(Stub(ResolutionAwareRepository) {
            createResolver() >> spyResolver
        })


        def componentSelectionRules = Stub(ComponentSelectionRulesInternal)

        when:
        def resolver = newFactory().createResolvers(repositories, Stub(ComponentMetadataProcessorFactory), componentSelectionRules, false, Mock(CacheExpirationControl), ImmutableAttributes.EMPTY, ImmutableAttributesSchema.EMPTY)

        then:
        assert resolver instanceof UserResolverChain
        resolver.componentSelectionRules == componentSelectionRules

        1 * spyResolver.setComponentResolvers(_) >> { ComponentResolvers parentResolver ->
            assert parentResolver instanceof ExternalModuleComponentResolverFactory.ParentModuleLookupResolver
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
        InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplierFactory = Stub()
        InstantiatingAction<ComponentMetadataListerDetails> versionListerFactory = Stub()

        return Spy(ExternalResourceResolver,
            constructorArgs: [
                Stub(UrlRepositoryDescriptor),
                false,
                externalResourceRepository,
                cacheAwareExternalResourceAccessor,
                locallyAvailableResourceFinder,
                artifactFileStore,
                metadataSources,
                metadataArtifactProvider,
                componentMetadataSupplierFactory,
                versionListerFactory,
                Mock(Instantiator),
                TestUtil.checksumService
            ]
        ) {
            appendId(_) >> {}
            getLocalAccess() >> Stub(ModuleComponentRepositoryAccess)
            getRemoteAccess() >> Stub(ModuleComponentRepositoryAccess)
            isM2compatible() >> true
        }
    }
}
