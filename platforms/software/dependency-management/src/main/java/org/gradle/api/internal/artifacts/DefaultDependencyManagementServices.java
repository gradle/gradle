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
package org.gradle.api.internal.artifacts;

import org.gradle.StartParameter;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyFactory;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentModuleMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyConstraintHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyConstraintFactoryInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.GradlePluginVariantsSupport;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.DefaultConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ShortCircuitEmptyConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultLocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DependencyGraphResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AdhocHandlingComponentResultSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.query.DefaultArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultUrlArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.artifacts.transform.DefaultTransformInvocationFactory;
import org.gradle.api.internal.artifacts.transform.DefaultTransformRegistrationFactory;
import org.gradle.api.internal.artifacts.transform.DefaultTransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.DefaultVariantSelectorFactory;
import org.gradle.api.internal.artifacts.transform.DefaultVariantTransformRegistry;
import org.gradle.api.internal.artifacts.transform.ImmutableTransformWorkspaceServices;
import org.gradle.api.internal.artifacts.transform.MutableTransformWorkspaceServices;
import org.gradle.api.internal.artifacts.transform.TransformActionScheme;
import org.gradle.api.internal.artifacts.transform.TransformExecutionListener;
import org.gradle.api.internal.artifacts.transform.TransformExecutionResult.TransformWorkspaceResult;
import org.gradle.api.internal.artifacts.transform.TransformInvocationFactory;
import org.gradle.api.internal.artifacts.transform.TransformParameterScheme;
import org.gradle.api.internal.artifacts.transform.TransformRegistrationFactory;
import org.gradle.api.internal.artifacts.transform.VariantSelectorFactory;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.DefaultAttributesSchema;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.Cache;
import org.gradle.cache.ManualEvictionInMemoryCache;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.build.BuildModelLifecycleListener;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.resolution.failure.ResolutionFailureDescriberRegistry;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionEngine.IdentityCacheResult;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.impl.NonLockingMutableWorkspaceProvider;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.locking.DefaultDependencyLockingHandler;
import org.gradle.internal.locking.DefaultDependencyLockingProvider;
import org.gradle.internal.locking.NoOpDependencyLockingProvider;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.util.internal.SimpleMapInterner;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultDependencyManagementServices implements DependencyManagementServices {

    private final ServiceRegistry parent;

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        this.parent = parent;
    }

    @Override
    public DependencyResolutionServices create(FileResolver resolver, FileCollectionFactory fileCollectionFactory, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        ServiceRegistry services = ServiceRegistryBuilder.builder()
            .parent(parent)
            .provider(registration -> {
                registration.add(FileResolver.class, resolver);
                registration.add(FileCollectionFactory.class, fileCollectionFactory);
                registration.add(DependencyMetaDataProvider.class, dependencyMetaDataProvider);
                registration.add(ProjectFinder.class, projectFinder);
                registration.add(DomainObjectContext.class, domainObjectContext);
            })
            .provider(new TransformGradleUserHomeServices())
            .provider(new DependencyResolutionScopeServices(domainObjectContext))
            .build();

        return services.get(DependencyResolutionServices.class);
    }

    @Override
    public void addDslServices(ServiceRegistration registration, DomainObjectContext domainObjectContext) {
        registration.addProvider(new DependencyResolutionScopeServices(domainObjectContext));
    }

    private static class TransformGradleUserHomeServices implements ServiceRegistrationProvider {
        @Provides
        TransformExecutionListener createTransformExecutionListener() {
            return new TransformExecutionListener() {
                @Override
                public void beforeTransformExecution(Describable transform, Describable subject) {
                }

                @Override
                public void afterTransformExecution(Describable transform, Describable subject) {
                }
            };
        }
    }

    private static class DependencyResolutionScopeServices implements ServiceRegistrationProvider {

        private final DomainObjectContext domainObjectContext;

        public DependencyResolutionScopeServices(DomainObjectContext domainObjectContext) {
            this.domainObjectContext = domainObjectContext;
        }

        void configure(ServiceRegistration registration) {
            registration.add(DefaultTransformedVariantFactory.class);
            registration.add(DefaultRootComponentMetadataBuilder.Factory.class);
            registration.add(ResolveExceptionMapper.class);
            registration.add(ResolutionStrategyFactory.class);
            registration.add(DefaultLocalComponentRegistry.class);
            registration.add(ProjectDependencyResolver.class);
            registration.add(ConsumerProvidedVariantFinder.class);
            registration.add(DefaultVariantSelectorFactory.class);
            registration.add(DefaultConfigurationFactory.class);
            registration.add(DefaultComponentSelectorConverter.class);
            registration.add(DefaultArtifactResolutionQueryFactory.class);
        }

        @Provides
        AttributesSchemaInternal createConfigurationAttributesSchema(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory, PlatformSupport platformSupport, ServiceRegistry serviceRegistry) {
            DefaultAttributesSchema attributesSchema = instantiatorFactory.decorateLenient().newInstance(DefaultAttributesSchema.class, instantiatorFactory.inject(serviceRegistry), isolatableFactory);
            platformSupport.configureSchema(attributesSchema);
            GradlePluginVariantsSupport.configureSchema(attributesSchema);
            return attributesSchema;
        }

        @Provides
        MutableTransformWorkspaceServices createTransformWorkspaceServices(ProjectLayout projectLayout, ExecutionHistoryStore executionHistoryStore) {
            Supplier<File> baseDirectory = projectLayout.getBuildDirectory().dir(".transforms").map(Directory::getAsFile)::get;
            Cache<UnitOfWork.Identity, IdentityCacheResult<TransformWorkspaceResult>> identityCache = new ManualEvictionInMemoryCache<>();
            return new MutableTransformWorkspaceServices() {
                @Override
                public MutableWorkspaceProvider getWorkspaceProvider() {
                    return new NonLockingMutableWorkspaceProvider(executionHistoryStore, baseDirectory.get());
                }

                @Override
                public Cache<UnitOfWork.Identity, IdentityCacheResult<TransformWorkspaceResult>> getIdentityCache() {
                    return identityCache;
                }

                @Override
                public Supplier<File> getReservedFileSystemLocation() {
                    return baseDirectory;
                }
            };
        }

        @Provides
        TransformInvocationFactory createTransformInvocationFactory(
            ExecutionEngine executionEngine,
            FileSystemAccess fileSystemAccess,
            InternalOptions internalOptions,
            ImmutableTransformWorkspaceServices transformWorkspaceServices,
            TransformExecutionListener transformExecutionListener,
            FileCollectionFactory fileCollectionFactory,
            ProjectStateRegistry projectStateRegistry,
            BuildOperationRunner buildOperationRunner,
            BuildOperationProgressEventEmitter progressEventEmitter
        ) {
            return new DefaultTransformInvocationFactory(
                executionEngine,
                fileSystemAccess,
                internalOptions,
                transformExecutionListener,
                transformWorkspaceServices,
                fileCollectionFactory,
                projectStateRegistry,
                buildOperationRunner,
                progressEventEmitter
            );
        }

        @Provides
        TransformRegistrationFactory createTransformRegistrationFactory(
            BuildOperationRunner buildOperationRunner,
            IsolatableFactory isolatableFactory,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            TransformInvocationFactory transformInvocationFactory,
            DomainObjectContext domainObjectContext,
            TransformParameterScheme parameterScheme,
            TransformActionScheme actionScheme,
            InputFingerprinter inputFingerprinter,
            CalculatedValueContainerFactory calculatedValueContainerFactory,
            FileCollectionFactory fileCollectionFactory,
            FileLookup fileLookup,
            ServiceRegistry internalServices
        ) {
            return new DefaultTransformRegistrationFactory(
                buildOperationRunner,
                isolatableFactory,
                classLoaderHierarchyHasher,
                transformInvocationFactory,
                fileCollectionFactory,
                fileLookup,
                inputFingerprinter,
                calculatedValueContainerFactory,
                domainObjectContext,
                parameterScheme,
                actionScheme,
                internalServices
            );
        }

        @Provides
        VariantTransformRegistry createVariantTransformRegistry(InstantiatorFactory instantiatorFactory, ImmutableAttributesFactory attributesFactory, ServiceRegistry services, TransformRegistrationFactory transformRegistrationFactory, TransformParameterScheme parameterScheme) {
            return new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, services, transformRegistrationFactory, parameterScheme.getInstantiationScheme());
        }

        @Provides
        DefaultUrlArtifactRepository.Factory createDefaultUrlArtifactRepositoryFactory(FileResolver fileResolver) {
            return new DefaultUrlArtifactRepository.Factory(fileResolver);
        }

        @Provides
        BaseRepositoryFactory createBaseRepositoryFactory(
            LocalMavenRepositoryLocator localMavenRepositoryLocator,
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            RepositoryTransportFactory repositoryTransportFactory,
            LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
            FileStoreAndIndexProvider fileStoreAndIndexProvider,
            VersionSelectorScheme versionSelectorScheme,
            AuthenticationSchemeRegistry authenticationSchemeRegistry,
            IvyContextManager ivyContextManager,
            ImmutableAttributesFactory attributesFactory,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            InstantiatorFactory instantiatorFactory,
            FileResourceRepository fileResourceRepository,
            MavenMutableModuleMetadataFactory metadataFactory,
            IvyMutableModuleMetadataFactory ivyMetadataFactory,
            IsolatableFactory isolatableFactory,
            ObjectFactory objectFactory,
            CollectionCallbackActionDecorator callbackDecorator,
            NamedObjectInstantiator instantiator,
            DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
            ChecksumService checksumService,
            ProviderFactory providerFactory,
            VersionParser versionParser
        ) {
            return new DefaultBaseRepositoryFactory(
                localMavenRepositoryLocator,
                fileResolver,
                fileCollectionFactory,
                repositoryTransportFactory,
                locallyAvailableResourceFinder,
                fileStoreAndIndexProvider.getArtifactIdentifierFileStore(),
                fileStoreAndIndexProvider.getExternalResourceFileStore(),
                new GradlePomModuleDescriptorParser(versionSelectorScheme, moduleIdentifierFactory, fileResourceRepository, metadataFactory),
                new GradleModuleMetadataParser(attributesFactory, moduleIdentifierFactory, instantiator),
                authenticationSchemeRegistry,
                ivyContextManager,
                moduleIdentifierFactory,
                instantiatorFactory,
                fileResourceRepository,
                metadataFactory,
                ivyMetadataFactory,
                isolatableFactory,
                objectFactory,
                callbackDecorator,
                urlArtifactRepositoryFactory,
                checksumService,
                providerFactory,
                versionParser
            );
        }

        @Provides
        RepositoryHandler createRepositoryHandler(Instantiator instantiator, BaseRepositoryFactory baseRepositoryFactory, CollectionCallbackActionDecorator callbackDecorator) {
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator, callbackDecorator);
        }

        @Provides
        ConfigurationContainerInternal createConfigurationContainer(
            Instantiator instantiator,
            CollectionCallbackActionDecorator callbackDecorator,
            DependencyMetaDataProvider dependencyMetaDataProvider,
            DomainObjectContext domainObjectContext,
            AttributesSchemaInternal attributesSchema,
            DefaultRootComponentMetadataBuilder.Factory rootComponentMetadataBuilderFactory,
            DefaultConfigurationFactory defaultConfigurationFactory,
            ResolutionStrategyFactory resolutionStrategyFactory
        ) {
            return instantiator.newInstance(DefaultConfigurationContainer.class,
                instantiator,
                callbackDecorator,
                dependencyMetaDataProvider,
                domainObjectContext,
                attributesSchema,
                rootComponentMetadataBuilderFactory,
                defaultConfigurationFactory,
                resolutionStrategyFactory
            );
        }

        @Provides
        PublishArtifactNotationParserFactory createPublishArtifactNotationParserFactory(
            Instantiator instantiator,
            DependencyMetaDataProvider metaDataProvider,
            FileResolver fileResolver,
            TaskDependencyFactory taskDependencyFactory
        ) {
            return new PublishArtifactNotationParserFactory(
                instantiator,
                metaDataProvider,
                fileResolver,
                taskDependencyFactory
            );
        }

        @Provides
        ArtifactTypeRegistry createArtifactTypeRegistry(Instantiator instantiator, ImmutableAttributesFactory immutableAttributesFactory, CollectionCallbackActionDecorator decorator, VariantTransformRegistry transformRegistry) {
            return new DefaultArtifactTypeRegistry(instantiator, immutableAttributesFactory, decorator, transformRegistry);
        }

        @Provides
        DependencyHandler createDependencyHandler(
            Instantiator instantiator,
            ConfigurationContainerInternal configurationContainer,
            DependencyFactoryInternal dependencyFactory,
            ProjectFinder projectFinder,
            DependencyConstraintHandler dependencyConstraintHandler,
            ComponentMetadataHandler componentMetadataHandler,
            ComponentModuleMetadataHandler componentModuleMetadataHandler,
            ArtifactResolutionQueryFactory resolutionQueryFactory,
            AttributesSchema attributesSchema,
            VariantTransformRegistry variantTransformRegistry,
            ArtifactTypeRegistry artifactTypeRegistry,
            ObjectFactory objects,
            PlatformSupport platformSupport
        ) {
            return instantiator.newInstance(DefaultDependencyHandler.class,
                configurationContainer,
                dependencyFactory,
                projectFinder,
                dependencyConstraintHandler,
                componentMetadataHandler,
                componentModuleMetadataHandler,
                resolutionQueryFactory,
                attributesSchema,
                variantTransformRegistry,
                artifactTypeRegistry,
                objects,
                platformSupport);
        }

        @Provides
        DependencyLockingHandler createDependencyLockingHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyLockingProvider dependencyLockingProvider) {
            if (domainObjectContext.isPluginContext()) {
                throw new IllegalStateException("Cannot use locking handler in plugins context");
            }
            // The lambda factory is to avoid eager creation of the configuration container
            return instantiator.newInstance(DefaultDependencyLockingHandler.class, (Supplier<ConfigurationContainerInternal>) () -> configurationContainer, dependencyLockingProvider);
        }

        @Provides
        DependencyLockingProvider createDependencyLockingProvider(FileResolver fileResolver, StartParameter startParameter, DomainObjectContext context, GlobalDependencyResolutionRules globalDependencyResolutionRules, ListenerManager listenerManager, PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory) {
            if (domainObjectContext.isPluginContext()) {
                return NoOpDependencyLockingProvider.getInstance();
            }

            DefaultDependencyLockingProvider dependencyLockingProvider = new DefaultDependencyLockingProvider(fileResolver, startParameter, context, globalDependencyResolutionRules.getDependencySubstitutionRules(), propertyFactory, filePropertyFactory, listenerManager.getBroadcaster(FileResourceListener.class));
            if (startParameter.isWriteDependencyLocks()) {
                listenerManager.addListener(new BuildModelLifecycleListener() {
                    @Override
                    public void beforeModelDiscarded(GradleInternal model, boolean buildFailed) {
                        if (!buildFailed) {
                            dependencyLockingProvider.buildFinished();
                        }
                    }
                });
            }
            return dependencyLockingProvider;
        }

        @Provides
        DependencyConstraintHandler createDependencyConstraintHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyConstraintFactoryInternal dependencyConstraintFactory, ObjectFactory objects, PlatformSupport platformSupport) {
            return instantiator.newInstance(DefaultDependencyConstraintHandler.class, configurationContainer, dependencyConstraintFactory, objects, platformSupport);
        }

        @Provides
        DefaultComponentMetadataHandler createComponentMetadataHandler(
            Instantiator instantiator,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            SimpleMapInterner interner,
            ImmutableAttributesFactory attributesFactory,
            IsolatableFactory isolatableFactory,
            ComponentMetadataRuleExecutor componentMetadataRuleExecutor,
            PlatformSupport platformSupport
        ) {
            DefaultComponentMetadataHandler componentMetadataHandler = instantiator.newInstance(DefaultComponentMetadataHandler.class, instantiator, moduleIdentifierFactory, interner, attributesFactory, isolatableFactory, componentMetadataRuleExecutor, platformSupport);
            if (domainObjectContext.isScript()) {
                componentMetadataHandler.setVariantDerivationStrategy(JavaEcosystemVariantDerivationStrategy.getInstance());
            }
            return componentMetadataHandler;
        }

        @Provides
        DefaultComponentModuleMetadataHandler createComponentModuleMetadataHandler(Instantiator instantiator, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            return instantiator.newInstance(DefaultComponentModuleMetadataHandler.class, moduleIdentifierFactory);
        }

        @Provides
        ArtifactHandler createArtifactHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, PublishArtifactNotationParserFactory publishArtifactNotationParserFactory) {
            return instantiator.newInstance(DefaultArtifactHandler.class, configurationContainer, publishArtifactNotationParserFactory.create());
        }

        @Provides
        ComponentMetadataProcessorFactory createComponentMetadataProcessorFactory(ComponentMetadataHandlerInternal componentMetadataHandler, DependencyResolutionManagementInternal dependencyResolutionManagement, DomainObjectContext context) {
            if (context.isScript()) {
                return componentMetadataHandler::createComponentMetadataProcessor;
            }
            return componentMetadataHandler.createFactory(dependencyResolutionManagement);
        }

        @Provides
        GlobalDependencyResolutionRules createModuleMetadataHandler(ComponentMetadataProcessorFactory componentMetadataProcessorFactory, ComponentModuleMetadataProcessor moduleMetadataProcessor, List<DependencySubstitutionRules> rules) {
            return new DefaultGlobalDependencyResolutionRules(componentMetadataProcessorFactory, moduleMetadataProcessor, rules);
        }

        @Provides
        ResolutionFailureHandler createResolutionFailureProcessor(InstantiatorFactory instantiatorFactory, ServiceRegistry serviceRegistry, InternalProblems problemsService) {
            InstanceGenerator instanceGenerator = instantiatorFactory.inject(serviceRegistry);
            ResolutionFailureDescriberRegistry failureDescriberRegistry = ResolutionFailureDescriberRegistry.standardRegistry(instanceGenerator);
            return new ResolutionFailureHandler(failureDescriberRegistry, problemsService);
        }

        @Provides
        GraphVariantSelector createGraphVariantSelector(ResolutionFailureHandler resolutionFailureHandler) {
            return new GraphVariantSelector(resolutionFailureHandler);
        }

        @Provides
        ConfigurationResolver createDependencyResolver(
            DependencyGraphResolver dependencyGraphResolver,
            RepositoriesSupplier repositoriesSupplier,
            GlobalDependencyResolutionRules metadataHandler,
            ResolutionResultsStoreFactory resolutionResultsStoreFactory,
            StartParameter startParameter,
            AttributesSchemaInternal attributesSchema,
            VariantSelectorFactory variantSelectorFactory,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            BuildOperationExecutor buildOperationExecutor,
            ArtifactTypeRegistry artifactTypeRegistry,
            CalculatedValueContainerFactory calculatedValueContainerFactory,
            ComponentSelectorConverter componentSelectorConverter,
            AttributeContainerSerializer attributeContainerSerializer,
            BuildState currentBuild,
            ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
            ResolvedArtifactSetResolver artifactSetResolver,
            AdhocHandlingComponentResultSerializer componentResultSerializer,
            ResolvedVariantCache resolvedVariantCache,
            GraphVariantSelector graphVariantSelector,
            ProjectStateRegistry projectStateRegistry,
            LocalComponentRegistry localComponentRegistry,
            List<ResolverProviderFactory> resolverFactories,
            ExternalModuleComponentResolverFactory moduleDependencyResolverFactory,
            ProjectDependencyResolver projectDependencyResolver,
            DependencyLockingProvider dependencyLockingProvider
        ) {
            DefaultConfigurationResolver defaultResolver = new DefaultConfigurationResolver(
                dependencyGraphResolver,
                repositoriesSupplier,
                metadataHandler,
                resolutionResultsStoreFactory,
                startParameter,
                attributesSchema,
                variantSelectorFactory,
                moduleIdentifierFactory,
                buildOperationExecutor,
                artifactTypeRegistry,
                calculatedValueContainerFactory,
                componentSelectorConverter,
                attributeContainerSerializer,
                currentBuild,
                artifactSetResolver,
                componentSelectionDescriptorFactory,
                componentResultSerializer,
                resolvedVariantCache,
                graphVariantSelector,
                projectStateRegistry,
                localComponentRegistry,
                resolverFactories,
                moduleDependencyResolverFactory,
                projectDependencyResolver,
                dependencyLockingProvider
            );

            return new ShortCircuitEmptyConfigurationResolver(defaultResolver);
        }

        @Provides
        ArtifactPublicationServices createArtifactPublicationServices(ServiceRegistry services) {
            return new DefaultArtifactPublicationServices(services);
        }

        @Provides
        DependencyResolutionServices createDependencyResolutionServices(ServiceRegistry services) {
            return new DefaultDependencyResolutionServices(services);
        }

        @Provides
        RepositoriesSupplier createRepositoriesSupplier(RepositoryHandler repositoryHandler, DependencyResolutionManagementInternal drm, DomainObjectContext context) {
            return () -> {
                List<ResolutionAwareRepository> repositories = collectRepositories(repositoryHandler);
                if (context.isScript() || context.isDetachedState()) {
                    return repositories;
                }
                DependencyResolutionManagementInternal.RepositoriesModeInternal mode = drm.getConfiguredRepositoriesMode();
                if (mode.useProjectRepositories()) {
                    if (repositories.isEmpty()) {
                        repositories = collectRepositories(drm.getRepositories());
                    }
                } else {
                    repositories = collectRepositories(drm.getRepositories());
                }
                return repositories;
            };
        }

        private static List<ResolutionAwareRepository> collectRepositories(RepositoryHandler repositoryHandler) {
            return repositoryHandler.stream()
                .map(ResolutionAwareRepository.class::cast)
                .collect(Collectors.toList());
        }
    }

    private static class DefaultDependencyResolutionServices implements DependencyResolutionServices {

        private final ServiceRegistry services;

        private DefaultDependencyResolutionServices(ServiceRegistry services) {
            this.services = services;
        }

        @Override
        public RepositoryHandler getResolveRepositoryHandler() {
            return services.get(RepositoryHandler.class);
        }

        @Override
        public ConfigurationContainerInternal getConfigurationContainer() {
            return services.get(ConfigurationContainerInternal.class);
        }

        @Override
        public DependencyHandler getDependencyHandler() {
            return services.get(DependencyHandler.class);
        }

        @Override
        public DependencyLockingHandler getDependencyLockingHandler() {
            return services.get(DependencyLockingHandler.class);
        }

        @Override
        public ImmutableAttributesFactory getAttributesFactory() {
            return services.get(ImmutableAttributesFactory.class);
        }

        @Override
        public AttributesSchema getAttributesSchema() {
            return services.get(AttributesSchema.class);
        }

        @Override
        public ObjectFactory getObjectFactory() {
            return services.get(ObjectFactory.class);
        }

        @Override
        public DependencyFactory getDependencyFactory() {
            return services.get(DependencyFactory.class);
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {

        private final ServiceRegistry services;

        public DefaultArtifactPublicationServices(ServiceRegistry services) {
            this.services = services;
        }

        @Override
        public RepositoryHandler createRepositoryHandler() {
            Instantiator instantiator = services.get(Instantiator.class);
            BaseRepositoryFactory baseRepositoryFactory = services.get(BaseRepositoryFactory.class);
            CollectionCallbackActionDecorator callbackDecorator = services.get(CollectionCallbackActionDecorator.class);
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator, callbackDecorator);
        }

    }
}
