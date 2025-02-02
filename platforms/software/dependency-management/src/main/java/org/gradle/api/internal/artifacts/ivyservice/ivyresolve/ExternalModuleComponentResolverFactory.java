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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.MetadataResolutionContext;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
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
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Actions;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.model.CalculatedValueFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;

/**
 * Creates resolvers that can resolve module components from repositories.
 */
@ServiceScope(Scope.Build.class)
public class ExternalModuleComponentResolverFactory {

    private final static Logger LOGGER = Logging.getLogger(ExternalModuleComponentResolverFactory.class);

    private final ModuleRepositoryCacheProvider cacheProvider;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final BuildCommencedTimeProvider timeProvider;
    private final VersionComparator versionComparator;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final RepositoryDisabler repositoryBlacklister;
    private final VersionParser versionParser;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final CalculatedValueFactory calculatedValueFactory;
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;
    private final ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor;

    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final ChangingValueDependencyResolutionListener listener;

    @Inject
    public ExternalModuleComponentResolverFactory(
        ModuleRepositoryCacheProvider cacheProvider,
        StartParameterResolutionOverride startParameterResolutionOverride,
        DependencyVerificationOverride dependencyVerificationOverride,
        BuildCommencedTimeProvider timeProvider,
        VersionComparator versionComparator,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        RepositoryDisabler repositoryBlacklister,
        VersionParser versionParser,
        ListenerManager listenerManager,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        CalculatedValueFactory calculatedValueFactory,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor
    ) {
        this.cacheProvider = cacheProvider;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.timeProvider = timeProvider;
        this.versionComparator = versionComparator;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.repositoryBlacklister = repositoryBlacklister;
        this.versionParser = versionParser;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.listener = listenerManager.getBroadcaster(ChangingValueDependencyResolutionListener.class);
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.calculatedValueFactory = calculatedValueFactory;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.componentMetadataSupplierRuleExecutor = componentMetadataSupplierRuleExecutor;
    }

    /**
     * Creates component resolvers for the given repositories.
     */
    public ComponentResolvers createResolvers(
        Collection<? extends ResolutionAwareRepository> repositories,
        ComponentMetadataProcessorFactory metadataProcessor,
        ComponentSelectionRulesInternal componentSelectionRules,
        boolean dependencyVerificationEnabled,
        CacheExpirationControl cacheExpirationControl,
        AttributeContainer consumerAttributes,
        ImmutableAttributesSchema consumerSchema
    ) {
        if (repositories.isEmpty()) {
            return new NoRepositoriesResolver();
        }

        UserResolverChain moduleResolver = new UserResolverChain(versionComparator, componentSelectionRules, versionParser, consumerAttributes, consumerSchema, attributesFactory, attributeSchemaServices, metadataProcessor, componentMetadataSupplierRuleExecutor, calculatedValueFactory, cacheExpirationControl);
        ParentModuleLookupResolver parentModuleResolver = new ParentModuleLookupResolver(versionComparator, moduleIdentifierFactory, versionParser, consumerAttributes, consumerSchema, attributesFactory, attributeSchemaServices, metadataProcessor, componentMetadataSupplierRuleExecutor, calculatedValueFactory, cacheExpirationControl);

        for (ResolutionAwareRepository repository : repositories) {
            ConfiguredModuleComponentRepository baseRepository = repository.createResolver();

            baseRepository.setComponentResolvers(parentModuleResolver);
            Instantiator instantiator = baseRepository.getComponentMetadataInstantiator();
            MetadataResolutionContext metadataResolutionContext = new DefaultMetadataResolutionContext(cacheExpirationControl, instantiator);
            ComponentMetadataProcessor componentMetadataProcessor = metadataProcessor.createComponentMetadataProcessor(metadataResolutionContext);

            ModuleComponentRepository<ExternalModuleComponentGraphResolveState> moduleComponentRepository;
            if (baseRepository.isLocal()) {
                moduleComponentRepository = new CachingModuleComponentRepository(baseRepository, cacheProvider.getInMemoryOnlyCaches(), moduleResolveStateFactory, cacheExpirationControl, timeProvider, componentMetadataProcessor, ChangingValueDependencyResolutionListener.NO_OP);
                moduleComponentRepository = new LocalModuleComponentRepository<>(moduleComponentRepository);
            } else {
                ModuleComponentRepository<ModuleComponentResolveMetadata> overrideRepository = startParameterResolutionOverride.overrideModuleVersionRepository(baseRepository);
                moduleComponentRepository = new CachingModuleComponentRepository(overrideRepository, cacheProvider.getPersistentCaches(), moduleResolveStateFactory, cacheExpirationControl, timeProvider, componentMetadataProcessor, listener);
            }
            moduleComponentRepository = cacheProvider.getResolvedArtifactCaches().provideResolvedArtifactCache(moduleComponentRepository, dependencyVerificationEnabled);

            if (baseRepository.isDynamicResolveMode()) {
                moduleComponentRepository = new IvyDynamicResolveModuleComponentRepository(moduleComponentRepository, moduleResolveStateFactory);
            }

            moduleComponentRepository = new ErrorHandlingModuleComponentRepository(moduleComponentRepository, repositoryBlacklister);
            moduleComponentRepository = filterRepository(repository, moduleComponentRepository);
            moduleComponentRepository = maybeApplyDependencyVerification(moduleComponentRepository, dependencyVerificationEnabled);

            moduleResolver.add(moduleComponentRepository);
            parentModuleResolver.add(moduleComponentRepository);
        }

        return moduleResolver;
    }

    private static ModuleComponentRepository<ExternalModuleComponentGraphResolveState> filterRepository(
        ResolutionAwareRepository repository,
        ModuleComponentRepository<ExternalModuleComponentGraphResolveState> moduleComponentRepository
    ) {
        Action<? super ArtifactResolutionDetails> filter = Actions.doNothing();
        if (repository instanceof ContentFilteringRepository) {
            filter = ((ContentFilteringRepository) repository).getContentFilter();
        }

        if (filter == Actions.doNothing()) {
            return moduleComponentRepository;
        }

        return new FilteredModuleComponentRepository(moduleComponentRepository, filter);
    }

    private ModuleComponentRepository<ExternalModuleComponentGraphResolveState> maybeApplyDependencyVerification(
        ModuleComponentRepository<ExternalModuleComponentGraphResolveState> moduleComponentRepository,
        boolean dependencyVerificationEnabled
    ) {
        if (!dependencyVerificationEnabled) {
            LOGGER.warn("Dependency verification has been disabled.");
            return moduleComponentRepository;
        }

        return dependencyVerificationOverride.overrideDependencyVerification(moduleComponentRepository);
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
            ImmutableAttributesSchema attributesSchema,
            AttributesFactory attributesFactory,
            AttributeSchemaServices attributeSchemaServices,
            ComponentMetadataProcessorFactory componentMetadataProcessorFactory,
            ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
            CalculatedValueFactory calculatedValueFactory,
            CacheExpirationControl cacheExpirationControl
        ) {
            this.delegate = new UserResolverChain(versionComparator, new DefaultComponentSelectionRules(moduleIdentifierFactory), versionParser, consumerAttributes, attributesSchema, attributesFactory, attributeSchemaServices, componentMetadataProcessorFactory, componentMetadataSupplierRuleExecutor, calculatedValueFactory, cacheExpirationControl);
        }

        public void add(ModuleComponentRepository<ExternalModuleComponentGraphResolveState> moduleComponentRepository) {
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
        public void resolve(ComponentSelector selector, ComponentOverrideMetadata overrideMetadata, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
            delegate.getComponentIdResolver().resolve(selector, overrideMetadata, acceptor, rejector, result);
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

        private final CacheExpirationControl cacheExpirationControl;
        private final Instantiator instantiator;

        private DefaultMetadataResolutionContext(CacheExpirationControl cacheExpirationControl, Instantiator instantiator) {
            this.cacheExpirationControl = cacheExpirationControl;
            this.instantiator = instantiator;
        }

        @Override
        public CacheExpirationControl getCacheExpirationControl() {
            return cacheExpirationControl;
        }

        @Override
        public Instantiator getInjectingInstantiator() {
            return instantiator;
        }
    }
}
