/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultComponentSelectionRules;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Collection;

public class ResolveIvyFactory {
    private final ModuleRepositoryCacheProvider cacheProvider;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final BuildCommencedTimeProvider timeProvider;
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final RepositoryBlacklister repositoryBlacklister;
    private final VersionParser versionParser;

    public ResolveIvyFactory(ModuleRepositoryCacheProvider cacheProvider,
                             StartParameterResolutionOverride startParameterResolutionOverride,
                             BuildCommencedTimeProvider timeProvider, VersionSelectorScheme versionSelectorScheme,
                             VersionComparator versionComparator, ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                             RepositoryBlacklister repositoryBlacklister,
                             VersionParser versionParser) {
        this.cacheProvider = cacheProvider;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.timeProvider = timeProvider;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.repositoryBlacklister = repositoryBlacklister;
        this.versionParser = versionParser;
    }

    public ComponentResolvers create(ResolutionStrategyInternal resolutionStrategy,
                                     Collection<? extends ResolutionAwareRepository> repositories,
                                     ComponentMetadataProcessor metadataProcessor,
                                     AttributeContainer consumerAttributes,
                                     AttributesSchema attributesSchema,
                                     ImmutableAttributesFactory attributesFactory,
                                     ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor) {
        if (repositories.isEmpty()) {
            return new NoRepositoriesResolver();
        }

        CachePolicy cachePolicy = resolutionStrategy.getCachePolicy();
        startParameterResolutionOverride.applyToCachePolicy(cachePolicy);

        UserResolverChain moduleResolver = new UserResolverChain(versionSelectorScheme, versionComparator, resolutionStrategy.getComponentSelection(), moduleIdentifierFactory, versionParser, consumerAttributes, attributesSchema, attributesFactory, metadataProcessor, componentMetadataSupplierRuleExecutor, cachePolicy);
        ParentModuleLookupResolver parentModuleResolver = new ParentModuleLookupResolver(versionSelectorScheme, versionComparator, moduleIdentifierFactory, versionParser, consumerAttributes, attributesSchema, attributesFactory, metadataProcessor, componentMetadataSupplierRuleExecutor, cachePolicy);

        for (ResolutionAwareRepository repository : repositories) {
            ConfiguredModuleComponentRepository baseRepository = repository.createResolver();

            if (baseRepository instanceof ExternalResourceResolver) {
                ((ExternalResourceResolver) baseRepository).setComponentResolvers(parentModuleResolver);
            }

            ModuleComponentRepository moduleComponentRepository = baseRepository;
            if (baseRepository.isLocal()) {
                moduleComponentRepository = new CachingModuleComponentRepository(moduleComponentRepository, cacheProvider.getInMemoryCaches(),
                    cachePolicy, timeProvider, metadataProcessor, moduleIdentifierFactory);
                moduleComponentRepository = new LocalModuleComponentRepository(moduleComponentRepository, metadataProcessor, cachePolicy);
            } else {
                moduleComponentRepository = startParameterResolutionOverride.overrideModuleVersionRepository(moduleComponentRepository);
                moduleComponentRepository = new CachingModuleComponentRepository(moduleComponentRepository, cacheProvider.getCaches(),
                    cachePolicy, timeProvider, metadataProcessor, moduleIdentifierFactory);
            }
            moduleComponentRepository = cacheProvider.getResolvedArtifactCaches().provideResolvedArtifactCache(moduleComponentRepository);

            if (baseRepository.isDynamicResolveMode()) {
                moduleComponentRepository = new IvyDynamicResolveModuleComponentRepository(moduleComponentRepository);
            }
            moduleComponentRepository = new ErrorHandlingModuleComponentRepository(moduleComponentRepository, repositoryBlacklister);

            moduleResolver.add(moduleComponentRepository);
            parentModuleResolver.add(moduleComponentRepository);
        }

        return moduleResolver;
    }

    /**
     * Provides access to the top-level resolver chain for looking up parent modules when parsing module descriptor files.
     */
    private static class ParentModuleLookupResolver implements ComponentResolvers, DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
        private final UserResolverChain delegate;

        public ParentModuleLookupResolver(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, ImmutableModuleIdentifierFactory moduleIdentifierFactory, VersionParser versionParser, AttributeContainer consumerAttributes, AttributesSchema attributesSchema, ImmutableAttributesFactory attributesFactory, ComponentMetadataProcessor componentMetadataProcessor, ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor, CachePolicy cachePolicy) {
            this.delegate = new UserResolverChain(versionSelectorScheme, versionComparator, new DefaultComponentSelectionRules(moduleIdentifierFactory), moduleIdentifierFactory, versionParser, consumerAttributes, attributesSchema, attributesFactory, componentMetadataProcessor, componentMetadataSupplierRuleExecutor, cachePolicy);
        }

        public void add(ModuleComponentRepository moduleComponentRepository) {
            delegate.add(moduleComponentRepository);
        }

        public DependencyToComponentIdResolver getComponentIdResolver() {
            return this;
        }

        public ComponentMetaDataResolver getComponentResolver() {
            return this;
        }

        public ArtifactResolver getArtifactResolver() {
            return this;
        }

        @Override
        public OriginArtifactSelector getArtifactSelector() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolve(final DependencyMetadata dependency, ResolvedVersionConstraint versionConstraint, final BuildableComponentIdResolveResult result) {
            delegate.getComponentIdResolver().resolve(dependency, versionConstraint, result);
        }

        @Override
        public void resolve(final ComponentIdentifier identifier, final ComponentOverrideMetadata componentOverrideMetadata, final BuildableComponentResolveResult result) {
            delegate.getComponentResolver().resolve(identifier, componentOverrideMetadata, result);
        }

        @Override
        public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
            return delegate.getComponentResolver().isFetchingMetadataCheap(identifier);
        }

        @Override
        public void resolveArtifactsWithType(final ComponentResolveMetadata component, final ArtifactType artifactType, final BuildableArtifactSetResolveResult result) {
            delegate.getArtifactResolver().resolveArtifactsWithType(component, artifactType, result);
        }

        @Override
        public void resolveArtifact(final ComponentArtifactMetadata artifact, final ModuleSource moduleSource, final BuildableArtifactResolveResult result) {
            delegate.getArtifactResolver().resolveArtifact(artifact, moduleSource, result);
        }
    }
}
