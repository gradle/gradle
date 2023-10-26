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

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.MetadataResolutionContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultComponentSelectionRules;
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Actions;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Creates resolver that can resolve module components from repositories.
 */
public class ResolveIvyFactory {
    private final ModuleRepositoryCacheProvider cacheProvider;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final BuildCommencedTimeProvider timeProvider;
    private final VersionComparator versionComparator;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final RepositoryDisabler repositoryBlacklister;
    private final VersionParser versionParser;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final ChangingValueDependencyResolutionListener listener;

    public ResolveIvyFactory(
        ModuleRepositoryCacheProvider cacheProvider,
        StartParameterResolutionOverride startParameterResolutionOverride,
        DependencyVerificationOverride dependencyVerificationOverride,
        BuildCommencedTimeProvider timeProvider,
        VersionComparator versionComparator,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        RepositoryDisabler repositoryBlacklister,
        VersionParser versionParser,
        ChangingValueDependencyResolutionListener listener,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.cacheProvider = cacheProvider;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.timeProvider = timeProvider;
        this.versionComparator = versionComparator;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.repositoryBlacklister = repositoryBlacklister;
        this.versionParser = versionParser;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.listener = listener;
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    public ComponentResolvers create(
        String resolveContextName,
        ResolutionStrategyInternal resolutionStrategy,
        Collection<? extends ResolutionAwareRepository> repositories,
        ComponentMetadataProcessorFactory metadataProcessor,
        AttributeContainer consumerAttributes,
        AttributesSchema attributesSchema,
        ImmutableAttributesFactory attributesFactory,
        ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor
    ) {
        if (repositories.isEmpty()) {
            return new NoRepositoriesResolver();
        }

        CachePolicy cachePolicy = resolutionStrategy.getCachePolicy();
        UserResolverChain moduleResolver = new UserResolverChain(versionComparator, resolutionStrategy.getComponentSelection(), versionParser, consumerAttributes, attributesSchema, attributesFactory, metadataProcessor, componentMetadataSupplierRuleExecutor, calculatedValueContainerFactory, cachePolicy);
        ParentModuleLookupResolver parentModuleResolver = new ParentModuleLookupResolver(versionComparator, moduleIdentifierFactory, versionParser, consumerAttributes, attributesSchema, attributesFactory, metadataProcessor, componentMetadataSupplierRuleExecutor, calculatedValueContainerFactory, cachePolicy);

        for (ResolutionAwareRepository repository : repositories) {
            ConfiguredModuleComponentRepository baseRepository = repository.createResolver();

            baseRepository.setComponentResolvers(parentModuleResolver);
            Instantiator instantiator = baseRepository.getComponentMetadataInstantiator();
            MetadataResolutionContext metadataResolutionContext = new DefaultMetadataResolutionContext(cachePolicy, instantiator);
            ComponentMetadataProcessor componentMetadataProcessor = metadataProcessor.createComponentMetadataProcessor(metadataResolutionContext);

            ModuleComponentRepository<ModuleComponentGraphResolveState> moduleComponentRepository;
            if (baseRepository.isLocal()) {
                moduleComponentRepository = new CachingModuleComponentRepository(baseRepository, cacheProvider.getInMemoryOnlyCaches(), moduleResolveStateFactory, cachePolicy, timeProvider, componentMetadataProcessor, ChangingValueDependencyResolutionListener.NO_OP, cacheProvider.getGuard());
                moduleComponentRepository = new LocalModuleComponentRepository<>(moduleComponentRepository);
            } else {
                ModuleComponentRepository<ModuleComponentResolveMetadata> overrideRepository = startParameterResolutionOverride.overrideModuleVersionRepository(baseRepository);
                moduleComponentRepository = new CachingModuleComponentRepository(overrideRepository, cacheProvider.getPersistentCaches(), moduleResolveStateFactory, cachePolicy, timeProvider, componentMetadataProcessor, listener, cacheProvider.getGuard());
            }
            moduleComponentRepository = cacheProvider.getResolvedArtifactCaches().provideResolvedArtifactCache(moduleComponentRepository, resolutionStrategy.isDependencyVerificationEnabled());

            if (baseRepository.isDynamicResolveMode()) {
                moduleComponentRepository = new IvyDynamicResolveModuleComponentRepository(moduleComponentRepository, moduleResolveStateFactory);
            }
            moduleComponentRepository = new ErrorHandlingModuleComponentRepository(moduleComponentRepository, repositoryBlacklister);
            moduleComponentRepository = filterRepository(repository, moduleComponentRepository, resolveContextName, consumerAttributes);
            moduleComponentRepository = dependencyVerificationOverride.overrideDependencyVerification(moduleComponentRepository, resolveContextName, resolutionStrategy);
            moduleResolver.add(moduleComponentRepository);
            parentModuleResolver.add(moduleComponentRepository);
        }

        return moduleResolver;
    }

    private ModuleComponentRepository<ModuleComponentGraphResolveState> filterRepository(ResolutionAwareRepository repository, ModuleComponentRepository<ModuleComponentGraphResolveState> moduleComponentRepository, String consumerName, AttributeContainer consumerAttributes) {
        Action<? super ArtifactResolutionDetails> filter = Actions.doNothing();
        if (repository instanceof ContentFilteringRepository) {
            filter = ((ContentFilteringRepository) repository).getContentFilter();
        }
        return FilteredModuleComponentRepository.of(moduleComponentRepository, filter, consumerName, consumerAttributes);
    }

    public ArtifactResult verifiedArtifact(DefaultResolvedArtifactResult defaultResolvedArtifactResult) {
        return dependencyVerificationOverride.verifiedArtifact(defaultResolvedArtifactResult);
    }

    /**
     * Provides access to the top-level resolver chain for looking up parent modules when parsing module descriptor files.
     */
    private static class ParentModuleLookupResolver implements ComponentResolvers, DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
        private final UserResolverChain delegate;

        public ParentModuleLookupResolver(
            VersionComparator versionComparator,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            VersionParser versionParser,
            AttributeContainer consumerAttributes,
            AttributesSchema attributesSchema,
            ImmutableAttributesFactory attributesFactory,
            ComponentMetadataProcessorFactory componentMetadataProcessorFactory,
            ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory,
            CachePolicy cachePolicy
        ) {
            this.delegate = new UserResolverChain(versionComparator, new DefaultComponentSelectionRules(moduleIdentifierFactory), versionParser, consumerAttributes, attributesSchema, attributesFactory, componentMetadataProcessorFactory, componentMetadataSupplierRuleExecutor, calculatedValueContainerFactory, cachePolicy);
        }

        public void add(ModuleComponentRepository<ModuleComponentGraphResolveState> moduleComponentRepository) {
            delegate.add(moduleComponentRepository);
        }

        @Override
        public DependencyToComponentIdResolver getComponentIdResolver() {
            return this;
        }

        @Override
        public ComponentMetaDataResolver getComponentResolver() {
            return this;
        }

        @Override
        public ArtifactResolver getArtifactResolver() {
            return this;
        }

        @Override
        public void resolve(DependencyMetadata dependency, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
            delegate.getComponentIdResolver().resolve(dependency, acceptor, rejector, result);
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
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            delegate.getArtifactResolver().resolveArtifactsWithType(component, artifactType, result);
        }

        @Override
        public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
            delegate.getArtifactResolver().resolveArtifact(component, artifact, result);
        }
    }

    private static class DefaultMetadataResolutionContext implements MetadataResolutionContext {

        private final CachePolicy cachePolicy;
        private final Instantiator instantiator;

        private DefaultMetadataResolutionContext(CachePolicy cachePolicy, Instantiator instantiator) {
            this.cachePolicy = cachePolicy;
            this.instantiator = instantiator;
        }

        @Override
        public CachePolicy getCachePolicy() {
            return cachePolicy;
        }

        @Override
        public Instantiator getInjectingInstantiator() {
            return instantiator;
        }
    }
}
