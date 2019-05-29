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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.StartParameter;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentModuleMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyConstraintHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.DefaultConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.ErrorHandlingConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualArtifactPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ShortCircuitEmptyConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.LocalComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.query.DefaultArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformParameterScheme;
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransforms;
import org.gradle.api.internal.artifacts.transform.DefaultTransformationRegistrationFactory;
import org.gradle.api.internal.artifacts.transform.DefaultTransformerInvoker;
import org.gradle.api.internal.artifacts.transform.DefaultVariantTransformRegistry;
import org.gradle.api.internal.artifacts.transform.DomainObjectProjectStateHandler;
import org.gradle.api.internal.artifacts.transform.ExecutionGraphDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.ImmutableCachingTransformationWorkspaceProvider;
import org.gradle.api.internal.artifacts.transform.MutableCachingTransformationWorkspaceProvider;
import org.gradle.api.internal.artifacts.transform.MutableTransformationWorkspaceProvider;
import org.gradle.api.internal.artifacts.transform.Transformation;
import org.gradle.api.internal.artifacts.transform.TransformationNode;
import org.gradle.api.internal.artifacts.transform.TransformationNodeRegistry;
import org.gradle.api.internal.artifacts.transform.TransformationRegistrationFactory;
import org.gradle.api.internal.artifacts.transform.TransformerInvoker;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.DefaultAttributesSchema;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Try;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.component.external.ivypublish.DefaultArtifactPublisher;
import org.gradle.internal.component.external.ivypublish.DefaultIvyModuleDescriptorWriter;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.CachingContext;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.IncrementalContext;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UpToDateResult;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.impl.DefaultWorkExecutor;
import org.gradle.internal.execution.steps.BroadcastChangingOutputsStep;
import org.gradle.internal.execution.steps.CatchExceptionStep;
import org.gradle.internal.execution.steps.CleanupOutputsStep;
import org.gradle.internal.execution.steps.CreateOutputsStep;
import org.gradle.internal.execution.steps.ExecuteStep;
import org.gradle.internal.execution.steps.ResolveChangesStep;
import org.gradle.internal.execution.steps.ResolveInputChangesStep;
import org.gradle.internal.execution.steps.SkipUpToDateStep;
import org.gradle.internal.execution.steps.SnapshotOutputsStep;
import org.gradle.internal.execution.steps.StoreSnapshotsStep;
import org.gradle.internal.execution.steps.TimeoutStep;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.locking.DefaultDependencyLockingHandler;
import org.gradle.internal.locking.DefaultDependencyLockingProvider;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.SimpleMapInterner;
import org.gradle.vcs.internal.VcsMappingsStore;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class DefaultDependencyManagementServices implements DependencyManagementServices {

    private final ServiceRegistry parent;

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        this.parent = parent;
    }

    @Override
    public DependencyResolutionServices create(FileResolver fileResolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        DefaultServiceRegistry services = new DefaultServiceRegistry(parent);
        services.add(FileResolver.class, fileResolver);
        services.add(DependencyMetaDataProvider.class, dependencyMetaDataProvider);
        services.add(ProjectFinder.class, projectFinder);
        services.add(DomainObjectContext.class, domainObjectContext);
        services.addProvider(new ArtifactTransformResolutionGradleUserHomeServices());
        services.addProvider(new DependencyResolutionScopeServices(domainObjectContext));
        return services.get(DependencyResolutionServices.class);
    }

    @Override
    public void addDslServices(ServiceRegistration registration, DomainObjectContext domainObjectContext) {
        registration.addProvider(new DependencyResolutionScopeServices(domainObjectContext));
    }

    private static class ArtifactTransformResolutionGradleUserHomeServices {

        ArtifactTransformListener createArtifactTransformListener() {
            return new ArtifactTransformListener() {
                @Override
                public void beforeTransformerInvocation(Describable transformer, Describable subject) {
                }

                @Override
                public void afterTransformerInvocation(Describable transformer, Describable subject) {
                }
            };
        }

        OutputFileCollectionFingerprinter createOutputFingerprinter(FileSystemSnapshotter fileSystemSnapshotter) {
            return new OutputFileCollectionFingerprinter(fileSystemSnapshotter);
        }

        TransformationNodeRegistry createTransformationNodeRegistry() {
            return new TransformationNodeRegistry() {
                @Override
                public Collection<TransformationNode> getOrCreate(ResolvedArtifactSet artifactSet, Transformation transformation, ExecutionGraphDependenciesResolver dependenciesResolver) {
                    throw new UnsupportedOperationException("Cannot schedule transforms for build script dependencies");
                }

                @Override
                public Optional<TransformationNode> getIfExecuted(ComponentArtifactIdentifier artifactId, Transformation transformation) {
                    return Optional.empty();
                }
            };
        }

        /**
         * Work executer for usage above Gradle scope
         *
         * Currently used for running artifact transformations in buildscript blocks.
         */
        WorkExecutor<IncrementalContext, CachingResult> createWorkExecutor(
            ExecutionStateChangeDetector changeDetector,
            ListenerManager listenerManager,
            TimeoutHandler timeoutHandler
        ) {
            OutputChangeListener outputChangeListener = listenerManager.getBroadcaster(OutputChangeListener.class);
            // TODO: Figure out how to get rid of origin scope id in snapshot outputs step
            UniqueId fixedUniqueId = UniqueId.from("dhwwyv4tqrd43cbxmdsf24wquu");
            return new DefaultWorkExecutor<>(
                new NoOpCachingStateStep(
                    new ResolveChangesStep<>(changeDetector,
                        new SkipUpToDateStep<>(
                            new BroadcastChangingOutputsStep<>(outputChangeListener,
                                new StoreSnapshotsStep<>(
                                    new SnapshotOutputsStep<>(fixedUniqueId,
                                        new CreateOutputsStep<>(
                                            new CatchExceptionStep<>(
                                                new TimeoutStep<>(timeoutHandler,
                                                    new ResolveInputChangesStep<>(
                                                        new CleanupOutputsStep<>(
                                                            new ExecuteStep<InputChangesContext>()
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            );
        }
    }

    private static class NoOpCachingStateStep implements Step<IncrementalContext, CachingResult> {
        private final Step<? super CachingContext, ? extends UpToDateResult> delegate;

        public NoOpCachingStateStep(Step<? super CachingContext, ? extends UpToDateResult> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CachingResult execute(IncrementalContext context) {
            UpToDateResult result = delegate.execute(new CachingContext() {
                @Override
                public CachingState getCachingState() {
                    return CachingState.NOT_DETERMINED;
                }

                @Override
                public Optional<String> getRebuildReason() {
                    return context.getRebuildReason();
                }

                @Override
                public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                    return context.getAfterPreviousExecutionState();
                }

                @Override
                public Optional<BeforeExecutionState> getBeforeExecutionState() {
                    return context.getBeforeExecutionState();
                }

                @Override
                public UnitOfWork getWork() {
                    return context.getWork();
                }
            });
            return new CachingResult() {
                @Override
                public CachingState getCachingState() {
                    return CachingState.NOT_DETERMINED;
                }

                @Override
                public ImmutableList<String> getExecutionReasons() {
                    return result.getExecutionReasons();
                }

                @Override
                public ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getFinalOutputs() {
                    return result.getFinalOutputs();
                }

                @Override
                public OriginMetadata getOriginMetadata() {
                    return result.getOriginMetadata();
                }

                @Override
                public boolean isReused() {
                    return result.isReused();
                }

                @Override
                public Try<ExecutionOutcome> getOutcome() {
                    return result.getOutcome();
                }
            };
        }
    }

    private static class DependencyResolutionScopeServices {

        private final DomainObjectContext domainObjectContext;

        public DependencyResolutionScopeServices(DomainObjectContext domainObjectContext) {
            this.domainObjectContext = domainObjectContext;
        }

        AttributesSchemaInternal createConfigurationAttributesSchema(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory) {
            return instantiatorFactory.decorateLenient().newInstance(DefaultAttributesSchema.class, new ComponentAttributeMatcher(), instantiatorFactory, isolatableFactory);
        }

        MutableTransformationWorkspaceProvider createTransformerWorkspaceProvider(ProjectLayout projectLayout, ExecutionHistoryStore executionHistoryStore) {
            return new MutableTransformationWorkspaceProvider(projectLayout.getBuildDirectory().dir(".transforms"), executionHistoryStore);
        }

        MutableCachingTransformationWorkspaceProvider createCachingTransformerWorkspaceProvider(MutableTransformationWorkspaceProvider workspaceProvider) {
            return new MutableCachingTransformationWorkspaceProvider(workspaceProvider);
        }

        TransformerInvoker createTransformerInvoker(WorkExecutor<IncrementalContext, CachingResult> workExecutor,
                                                    FileSystemSnapshotter fileSystemSnapshotter,
                                                    ImmutableCachingTransformationWorkspaceProvider transformationWorkspaceProvider,
                                                    ArtifactTransformListener artifactTransformListener,
                                                    FileCollectionFactory fileCollectionFactory,
                                                    ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
                                                    ProjectFinder projectFinder,
                                                    BuildOperationExecutor buildOperationExecutor
        ) {
            return new DefaultTransformerInvoker(
                workExecutor,
                fileSystemSnapshotter,
                artifactTransformListener,
                transformationWorkspaceProvider,
                fileCollectionFactory,
                classLoaderHierarchyHasher,
                projectFinder,
                buildOperationExecutor
            );
        }

        TransformationRegistrationFactory createTransformationRegistrationFactory(
            IsolatableFactory isolatableFactory,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            TransformerInvoker transformerInvoker,
            ValueSnapshotter valueSnapshotter,
            ProjectStateRegistry projectStateRegistry,
            DomainObjectContext domainObjectContext,
            ProjectFinder projectFinder,
            ArtifactTransformParameterScheme parameterScheme,
            ArtifactTransformActionScheme actionScheme,
            FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry,
            FileCollectionFactory fileCollectionFactory
        ) {
            return new DefaultTransformationRegistrationFactory(
                isolatableFactory,
                classLoaderHierarchyHasher,
                transformerInvoker,
                valueSnapshotter,
                fileCollectionFactory,
                fileCollectionFingerprinterRegistry,
                new DomainObjectProjectStateHandler(projectStateRegistry, domainObjectContext, projectFinder),
                parameterScheme,
                actionScheme
            );
        }

        VariantTransformRegistry createArtifactTransformRegistry(InstantiatorFactory instantiatorFactory, ImmutableAttributesFactory attributesFactory, ServiceRegistry services, TransformationRegistrationFactory transformationRegistrationFactory, ArtifactTransformParameterScheme parameterScheme) {
            return new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, services, transformationRegistrationFactory, parameterScheme.getInstantiationScheme());
        }

        BaseRepositoryFactory createBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                                          FileResolver fileResolver,
                                                          RepositoryTransportFactory repositoryTransportFactory,
                                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                                          ArtifactIdentifierFileStore artifactIdentifierFileStore,
                                                          ExternalResourceFileStore externalResourceFileStore,
                                                          VersionSelectorScheme versionSelectorScheme,
                                                          AuthenticationSchemeRegistry authenticationSchemeRegistry,
                                                          IvyContextManager ivyContextManager,
                                                          ImmutableAttributesFactory attributesFactory,
                                                          ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                          InstantiatorFactory instantiatorFactory,
                                                          FileResourceRepository fileResourceRepository,
                                                          FeaturePreviews featurePreviews,
                                                          MavenMutableModuleMetadataFactory metadataFactory,
                                                          IvyMutableModuleMetadataFactory ivyMetadataFactory,
                                                          IsolatableFactory isolatableFactory,
                                                          ObjectFactory objectFactory,
                                                          CollectionCallbackActionDecorator callbackDecorator) {
            return new DefaultBaseRepositoryFactory(
                localMavenRepositoryLocator,
                fileResolver,
                repositoryTransportFactory,
                locallyAvailableResourceFinder,
                artifactIdentifierFileStore,
                externalResourceFileStore,
                new GradlePomModuleDescriptorParser(versionSelectorScheme, moduleIdentifierFactory, fileResourceRepository, metadataFactory),
                new GradleModuleMetadataParser(attributesFactory, moduleIdentifierFactory, NamedObjectInstantiator.INSTANCE),
                authenticationSchemeRegistry,
                ivyContextManager,
                moduleIdentifierFactory,
                instantiatorFactory,
                fileResourceRepository,
                featurePreviews,
                metadataFactory,
                ivyMetadataFactory,
                isolatableFactory,
                objectFactory,
                callbackDecorator);
        }

        RepositoryHandler createRepositoryHandler(Instantiator instantiator, BaseRepositoryFactory baseRepositoryFactory, CollectionCallbackActionDecorator callbackDecorator) {
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator, callbackDecorator);
        }

        ConfigurationContainerInternal createConfigurationContainer(Instantiator instantiator, ConfigurationResolver configurationResolver, DomainObjectContext domainObjectContext,
                                                                    ListenerManager listenerManager, DependencyMetaDataProvider metaDataProvider, ProjectAccessListener projectAccessListener,
                                                                    ProjectFinder projectFinder, LocalComponentMetadataBuilder metaDataBuilder, FileCollectionFactory fileCollectionFactory,
                                                                    GlobalDependencyResolutionRules globalDependencyResolutionRules, VcsMappingsStore vcsMappingsStore, ComponentIdentifierFactory componentIdentifierFactory,
                                                                    BuildOperationExecutor buildOperationExecutor, ImmutableAttributesFactory attributesFactory,
                                                                    ImmutableModuleIdentifierFactory moduleIdentifierFactory, ComponentSelectorConverter componentSelectorConverter,
                                                                    DependencyLockingProvider dependencyLockingProvider,
                                                                    ProjectStateRegistry projectStateRegistry,
                                                                    DocumentationRegistry documentationRegistry,
                                                                    CollectionCallbackActionDecorator callbackDecorator,
                                                                    UserCodeApplicationContext userCodeApplicationContext,
                                                                    DomainObjectCollectionFactory domainObjectCollectionFactory) {
            return instantiator.newInstance(DefaultConfigurationContainer.class,
                configurationResolver,
                instantiator,
                domainObjectContext,
                listenerManager,
                metaDataProvider,
                projectAccessListener,
                projectFinder,
                metaDataBuilder,
                fileCollectionFactory,
                globalDependencyResolutionRules.getDependencySubstitutionRules(),
                vcsMappingsStore,
                componentIdentifierFactory,
                buildOperationExecutor,
                taskResolverFor(domainObjectContext),
                attributesFactory,
                moduleIdentifierFactory,
                componentSelectorConverter,
                dependencyLockingProvider,
                projectStateRegistry,
                documentationRegistry,
                callbackDecorator,
                userCodeApplicationContext,
                domainObjectCollectionFactory
            );
        }

        @Nullable
        private TaskResolver taskResolverFor(DomainObjectContext domainObjectContext) {
            if (domainObjectContext instanceof ProjectInternal) {
                return ((ProjectInternal) domainObjectContext).getTasks();
            }
            return null;
        }

        ArtifactTypeRegistry createArtifactTypeRegistry(Instantiator instantiator, ImmutableAttributesFactory immutableAttributesFactory, CollectionCallbackActionDecorator decorator) {
            return new DefaultArtifactTypeRegistry(instantiator, immutableAttributesFactory, decorator);
        }

        DependencyHandler createDependencyHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyFactory dependencyFactory,
                                                  ProjectFinder projectFinder, DependencyConstraintHandler dependencyConstraintHandler, ComponentMetadataHandler componentMetadataHandler, ComponentModuleMetadataHandler componentModuleMetadataHandler, ArtifactResolutionQueryFactory resolutionQueryFactory, AttributesSchema attributesSchema, VariantTransformRegistry artifactTransformRegistrations, ArtifactTypeRegistry artifactTypeRegistry) {
            return instantiator.newInstance(DefaultDependencyHandler.class,
                configurationContainer,
                dependencyFactory,
                projectFinder,
                dependencyConstraintHandler,
                componentMetadataHandler,
                componentModuleMetadataHandler,
                resolutionQueryFactory,
                attributesSchema,
                artifactTransformRegistrations,
                artifactTypeRegistry,
                NamedObjectInstantiator.INSTANCE);
        }

        DependencyLockingHandler createDependencyLockingHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer) {
            return instantiator.newInstance(DefaultDependencyLockingHandler.class, configurationContainer);
        }

        DependencyLockingProvider createDependencyLockingProvider(Instantiator instantiator, FileResolver fileResolver, StartParameter startParameter, DomainObjectContext context, GlobalDependencyResolutionRules globalDependencyResolutionRules) {
            return instantiator.newInstance(DefaultDependencyLockingProvider.class, fileResolver, startParameter, context, globalDependencyResolutionRules.getDependencySubstitutionRules());
        }

        DependencyConstraintHandler createDependencyConstraintHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyFactory dependencyFactory, ComponentMetadataHandler componentMetadataHandler) {
            return instantiator.newInstance(DefaultDependencyConstraintHandler.class, configurationContainer, dependencyFactory, NamedObjectInstantiator.INSTANCE);
        }

        DefaultComponentMetadataHandler createComponentMetadataHandler(Instantiator instantiator, ImmutableModuleIdentifierFactory moduleIdentifierFactory, SimpleMapInterner interner, ImmutableAttributesFactory attributesFactory, IsolatableFactory isolatableFactory, ComponentMetadataRuleExecutor componentMetadataRuleExecutor) {
            DefaultComponentMetadataHandler componentMetadataHandler = instantiator.newInstance(DefaultComponentMetadataHandler.class, instantiator, moduleIdentifierFactory, interner, attributesFactory, isolatableFactory, componentMetadataRuleExecutor);
            if (domainObjectContext.isScript()) {
                componentMetadataHandler.setVariantDerivationStrategy(new JavaEcosystemVariantDerivationStrategy());
            }
            return componentMetadataHandler;
        }

        DefaultComponentModuleMetadataHandler createComponentModuleMetadataHandler(Instantiator instantiator, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            return instantiator.newInstance(DefaultComponentModuleMetadataHandler.class, moduleIdentifierFactory);
        }

        ArtifactHandler createArtifactHandler(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, ConfigurationContainerInternal configurationContainer, DomainObjectContext context) {
            NotationParser<Object, ConfigurablePublishArtifact> publishArtifactNotationParser = new PublishArtifactNotationParserFactory(instantiator, dependencyMetaDataProvider, taskResolverFor(context)).create();
            return instantiator.newInstance(DefaultArtifactHandler.class, configurationContainer, publishArtifactNotationParser);
        }

        GlobalDependencyResolutionRules createModuleMetadataHandler(ComponentMetadataProcessorFactory componentMetadataProcessorFactory, ComponentModuleMetadataProcessor moduleMetadataProcessor, List<DependencySubstitutionRules> rules) {
            return new DefaultGlobalDependencyResolutionRules(componentMetadataProcessorFactory, moduleMetadataProcessor, rules);
        }

        ConfigurationResolver createDependencyResolver(ArtifactDependencyResolver artifactDependencyResolver,
                                                       RepositoryHandler repositories,
                                                       GlobalDependencyResolutionRules metadataHandler,
                                                       ComponentIdentifierFactory componentIdentifierFactory,
                                                       ResolutionResultsStoreFactory resolutionResultsStoreFactory,
                                                       StartParameter startParameter,
                                                       AttributesSchemaInternal attributesSchema,
                                                       VariantTransformRegistry variantTransforms,
                                                       ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                       ImmutableAttributesFactory attributesFactory,
                                                       BuildOperationExecutor buildOperationExecutor,
                                                       ArtifactTypeRegistry artifactTypeRegistry,
                                                       ComponentSelectorConverter componentSelectorConverter,
                                                       AttributeContainerSerializer attributeContainerSerializer,
                                                       BuildState currentBuild,
                                                       TransformationNodeRegistry transformationNodeRegistry) {
            return new ErrorHandlingConfigurationResolver(
                    new ShortCircuitEmptyConfigurationResolver(
                        new DefaultConfigurationResolver(
                            artifactDependencyResolver,
                            repositories,
                            metadataHandler,
                            resolutionResultsStoreFactory,
                            startParameter.isBuildProjectDependencies(),
                            attributesSchema,
                            new DefaultArtifactTransforms(
                                new ConsumerProvidedVariantFinder(
                                    variantTransforms,
                                    attributesSchema,
                                    attributesFactory),
                                attributesSchema,
                                attributesFactory,
                                transformationNodeRegistry
                            ),
                            moduleIdentifierFactory,
                            buildOperationExecutor,
                            artifactTypeRegistry,
                            componentSelectorConverter,
                            attributeContainerSerializer,
                            currentBuild.getBuildIdentifier()
                        ),
                        componentIdentifierFactory,
                        moduleIdentifierFactory,
                        currentBuild.getBuildIdentifier()));
        }

        ArtifactPublicationServices createArtifactPublicationServices(ServiceRegistry services) {
            return new DefaultArtifactPublicationServices(services);
        }

        DependencyResolutionServices createDependencyResolutionServices(ServiceRegistry services) {
            return new DefaultDependencyResolutionServices(services, domainObjectContext);
        }

        ArtifactResolutionQueryFactory createArtifactResolutionQueryFactory(ConfigurationContainerInternal configurationContainer, RepositoryHandler repositoryHandler,
                                                                            ResolveIvyFactory ivyFactory, GlobalDependencyResolutionRules metadataHandler,
                                                                            ComponentTypeRegistry componentTypeRegistry, ImmutableAttributesFactory attributesFactory, ComponentMetadataSupplierRuleExecutor executor) {
            return new DefaultArtifactResolutionQueryFactory(configurationContainer, repositoryHandler, ivyFactory, metadataHandler, componentTypeRegistry, attributesFactory, executor);

        }

    }

    private static class DefaultDependencyResolutionServices implements DependencyResolutionServices {

        private final ServiceRegistry services;
        private final DomainObjectContext domainObjectContext;

        private DefaultDependencyResolutionServices(ServiceRegistry services, DomainObjectContext domainObjectContext) {
            this.services = services;
            this.domainObjectContext = domainObjectContext;
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

        @Override
        public ArtifactPublisher createArtifactPublisher() {
            DefaultArtifactPublisher publisher = new DefaultArtifactPublisher(
                services.get(LocalConfigurationMetadataBuilder.class),
                new DefaultIvyModuleDescriptorWriter(services.get(ComponentSelectorConverter.class))
            );
            return new IvyContextualArtifactPublisher(services.get(IvyContextManager.class), publisher);
        }

    }
}
