/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.component.DefaultComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryDisabler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CachingVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleComponentResolveMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleSourcesSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.SuppliedComponentMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DependencyGraphResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.transform.TransformStepNodeDependencyResolver;
import org.gradle.api.internal.artifacts.verification.signatures.DefaultSignatureVerificationServiceFactory;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.catalog.DefaultDependenciesAccessors;
import org.gradle.api.internal.catalog.DependenciesAccessorsWorkspaceProvider;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyConstraintNotationParser;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.BuildTreeObjectFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.internal.CleaningInMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.GeneratedGradleJarCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.build.BuildModelLifecycleListener;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.buildoption.FeatureFlags;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.component.ResolutionFailureHandler;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.impl.DefaultExecutionEngine;
import org.gradle.internal.execution.steps.AssignWorkspaceStep;
import org.gradle.internal.execution.steps.CachingContext;
import org.gradle.internal.execution.steps.CachingResult;
import org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep;
import org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep;
import org.gradle.internal.execution.steps.CreateOutputsStep;
import org.gradle.internal.execution.steps.ExecuteStep;
import org.gradle.internal.execution.steps.IdentifyStep;
import org.gradle.internal.execution.steps.IdentityCacheStep;
import org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep;
import org.gradle.internal.execution.steps.RemovePreviousOutputsStep;
import org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep;
import org.gradle.internal.execution.steps.ResolveChangesStep;
import org.gradle.internal.execution.steps.ResolveInputChangesStep;
import org.gradle.internal.execution.steps.SkipUpToDateStep;
import org.gradle.internal.execution.steps.Step;
import org.gradle.internal.execution.steps.StoreExecutionStateStep;
import org.gradle.internal.execution.steps.TimeoutStep;
import org.gradle.internal.execution.steps.UpToDateResult;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.execution.steps.ValidationFinishedContext;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.management.DefaultDependencyResolutionManagement;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.local.FileResourceConnector;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.internal.resource.transfer.CachingTextUriResourceLoader;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.gradle.util.internal.SimpleMapInterner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The set of dependency management services that are created per build in the tree.
 */
class DependencyManagementBuildScopeServices {
    void configure(ServiceRegistration registration) {
        registration.add(TransformStepNodeDependencyResolver.class);
        registration.add(DefaultProjectPublicationRegistry.class);
        registration.add(FileResourceConnector.class);
        registration.add(DefaultComponentSelectorConverter.class);
        registration.add(ProjectDependencyResolver.class);
        registration.add(DependencyGraphResolver.class);
    }

    DependencyResolutionManagementInternal createSharedDependencyResolutionServices(
        Instantiator instantiator,
        UserCodeApplicationContext context,
        DependencyManagementServices dependencyManagementServices,
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        DependencyMetaDataProvider dependencyMetaDataProvider,
        ObjectFactory objects,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator
    ) {
        return instantiator.newInstance(DefaultDependencyResolutionManagement.class,
            context,
            dependencyManagementServices,
            fileResolver,
            fileCollectionFactory,
            dependencyMetaDataProvider,
            objects,
            collectionCallbackActionDecorator
        );
    }

    DependencyManagementServices createDependencyManagementServices(ServiceRegistry parent) {
        return new DefaultDependencyManagementServices(parent);
    }

    ComponentIdentifierFactory createComponentIdentifierFactory(BuildState currentBuild, BuildStateRegistry buildRegistry) {
        return new DefaultComponentIdentifierFactory(buildRegistry.getBuild(currentBuild.getBuildIdentifier()));
    }

    VersionComparator createVersionComparator() {
        return new DefaultVersionComparator();
    }

    CapabilityNotationParser createCapabilityNotationParser() {
        return new CapabilityNotationParserFactory(false).create();
    }

    DefaultProjectDependencyFactory createProjectDependencyFactory(
        Instantiator instantiator,
        StartParameter startParameter,
        ImmutableAttributesFactory attributesFactory,
        TaskDependencyFactory taskDependencyFactory,
        CapabilityNotationParser capabilityNotationParser
    ) {
        return new DefaultProjectDependencyFactory(instantiator, startParameter.isBuildProjectDependencies(), capabilityNotationParser, attributesFactory, taskDependencyFactory);
    }

    DependencyFactoryInternal createDependencyFactory(
        Instantiator instantiator,
        DefaultProjectDependencyFactory factory,
        ClassPathRegistry classPathRegistry,
        CurrentGradleInstallation currentGradleInstallation,
        FileCollectionFactory fileCollectionFactory,
        RuntimeShadedJarFactory runtimeShadedJarFactory,
        ImmutableAttributesFactory attributesFactory,
        SimpleMapInterner stringInterner,
        CapabilityNotationParser capabilityNotationParser
    ) {
        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);

        return new DefaultDependencyFactory(
            instantiator,
            DependencyNotationParser.create(instantiator, factory, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory, currentGradleInstallation, stringInterner),
            DependencyConstraintNotationParser.parser(instantiator, factory, stringInterner, attributesFactory),
            new ClientModuleNotationParserFactory(instantiator, stringInterner).create(),
            capabilityNotationParser, projectDependencyFactory,
            attributesFactory);
    }

    RuntimeShadedJarFactory createRuntimeShadedJarFactory(GeneratedGradleJarCache jarCache, ProgressLoggerFactory progressLoggerFactory, ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder, BuildOperationExecutor executor) {
        return new RuntimeShadedJarFactory(jarCache, progressLoggerFactory, classpathWalker, classpathBuilder, executor);
    }

    ModuleExclusions createModuleExclusions() {
        return new ModuleExclusions();
    }

    TextUriResourceLoader.Factory createTextUrlResourceLoaderFactory(FileStoreAndIndexProvider fileStoreAndIndexProvider, RepositoryTransportFactory repositoryTransportFactory, RelativeFilePathResolver resolver) {
        final HashSet<String> schemas = Sets.newHashSet("https", "http");
        return redirectVerifier -> {
            RepositoryTransport transport = repositoryTransportFactory.createTransport(schemas, "resources http", Collections.emptyList(), redirectVerifier);
            ExternalResourceAccessor externalResourceAccessor = new DefaultExternalResourceAccessor(fileStoreAndIndexProvider.getExternalResourceFileStore(), transport.getResourceAccessor());
            return new CachingTextUriResourceLoader(externalResourceAccessor, schemas, resolver);
        };
    }

    protected ApiTextResourceAdapter.Factory createTextResourceAdapterFactory(TextUriResourceLoader.Factory textUriResourceLoaderFactory, TemporaryFileProvider tempFileProvider) {
        return new ApiTextResourceAdapter.Factory(textUriResourceLoaderFactory, tempFileProvider);
    }

    MavenSettingsProvider createMavenSettingsProvider() {
        return new DefaultMavenSettingsProvider(new DefaultMavenFileLocations());
    }

    LocalMavenRepositoryLocator createLocalMavenRepositoryLocator(MavenSettingsProvider mavenSettingsProvider) {
        return new DefaultLocalMavenRepositoryLocator(mavenSettingsProvider);
    }

    LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> createArtifactRevisionIdLocallyAvailableResourceFinder(
        ArtifactCachesProvider artifactCaches,
        LocalMavenRepositoryLocator localMavenRepositoryLocator,
        FileStoreAndIndexProvider fileStoreAndIndexProvider,
        ChecksumService checksumService
    ) {
        LocallyAvailableResourceFinderFactory finderFactory = new LocallyAvailableResourceFinderFactory(
            artifactCaches,
            localMavenRepositoryLocator,
            fileStoreAndIndexProvider.getArtifactIdentifierFileStore(), checksumService);
        return finderFactory.create();
    }

    RepositoryTransportFactory createRepositoryTransportFactory(
        TemporaryFileProvider temporaryFileProvider,
        FileStoreAndIndexProvider fileStoreAndIndexProvider,
        BuildCommencedTimeProvider buildCommencedTimeProvider,
        ArtifactCachesProvider artifactCachesProvider,
        List<ResourceConnectorFactory> resourceConnectorFactories,
        BuildOperationExecutor buildOperationExecutor,
        ProducerGuard<ExternalResourceName> producerGuard,
        FileResourceRepository fileResourceRepository,
        ChecksumService checksumService,
        StartParameterResolutionOverride startParameterResolutionOverride
    ) {
        return artifactCachesProvider.withWritableCache((md, manager) -> new RepositoryTransportFactory(
            resourceConnectorFactories,
            temporaryFileProvider,
            fileStoreAndIndexProvider.getExternalResourceIndex(),
            buildCommencedTimeProvider,
            manager,
            buildOperationExecutor,
            startParameterResolutionOverride,
            producerGuard,
            fileResourceRepository,
            checksumService
        ));
    }

    DependencyVerificationOverride createDependencyVerificationOverride(
        StartParameterResolutionOverride startParameterResolutionOverride,
        BuildOperationExecutor buildOperationExecutor,
        ChecksumService checksumService,
        SignatureVerificationServiceFactory signatureVerificationServiceFactory,
        DocumentationRegistry documentationRegistry,
        ListenerManager listenerManager,
        BuildCommencedTimeProvider timeProvider,
        ServiceRegistry serviceRegistry
    ) {
        DependencyVerificationOverride override = startParameterResolutionOverride.dependencyVerificationOverride(buildOperationExecutor, checksumService, signatureVerificationServiceFactory, documentationRegistry, timeProvider, () -> serviceRegistry.get(GradleProperties.class), listenerManager.getBroadcaster(FileResourceListener.class));
        registerBuildFinishedHooks(listenerManager, override);
        return override;
    }

    ResolveIvyFactory createResolveIvyFactory(
        StartParameterResolutionOverride startParameterResolutionOverride,
        ModuleRepositoryCacheProvider moduleRepositoryCacheProvider,
        DependencyVerificationOverride dependencyVerificationOverride,
        BuildCommencedTimeProvider buildCommencedTimeProvider,
        VersionComparator versionComparator,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        RepositoryDisabler repositoryBlacklister,
        VersionParser versionParser,
        ListenerManager listenerManager,
        ModuleComponentGraphResolveStateFactory resolveStateFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return new ResolveIvyFactory(
            moduleRepositoryCacheProvider,
            startParameterResolutionOverride,
            dependencyVerificationOverride,
            buildCommencedTimeProvider,
            versionComparator,
            moduleIdentifierFactory,
            repositoryBlacklister,
            versionParser,
            listenerManager.getBroadcaster(ChangingValueDependencyResolutionListener.class),
            resolveStateFactory,
            calculatedValueContainerFactory);
    }

    ResolvedVariantCache createResolvedVariantCache() {
        ConcurrentHashMap<VariantResolveMetadata.Identifier, ResolvedVariant> map = new ConcurrentHashMap<>();
        return new ResolvedVariantCache() {
            @Override
            public ResolvedVariant computeIfAbsent(VariantResolveMetadata.Identifier key, Function<? super VariantResolveMetadata.Identifier, ? extends ResolvedVariant> mappingFunction) {
                return map.computeIfAbsent(key, mappingFunction);
            }
        };
    }

    ResolutionFailureHandler createResolutionFailureProcessor(Problems problems, DocumentationRegistry documentationRegistry, ProviderFactory providerFactory) {
        return new ResolutionFailureHandler(problems, documentationRegistry, providerFactory);
    }

    GraphVariantSelector createGraphVariantSelector(ResolutionFailureHandler resolutionFailureHandler) {
        return new GraphVariantSelector(resolutionFailureHandler);
    }

    VersionSelectorScheme createVersionSelectorScheme(VersionComparator versionComparator, VersionParser versionParser) {
        DefaultVersionSelectorScheme delegate = new DefaultVersionSelectorScheme(versionComparator, versionParser);
        CachingVersionSelectorScheme selectorScheme = new CachingVersionSelectorScheme(delegate);
        return selectorScheme;
    }

    ModuleComponentResolveMetadataSerializer createModuleComponentResolveMetadataSerializer(ImmutableAttributesFactory attributesFactory, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, NamedObjectInstantiator instantiator, ModuleSourcesSerializer moduleSourcesSerializer) {
        DesugaringAttributeContainerSerializer attributeContainerSerializer = new DesugaringAttributeContainerSerializer(attributesFactory, instantiator);
        return new ModuleComponentResolveMetadataSerializer(new ModuleMetadataSerializer(attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, moduleSourcesSerializer), attributeContainerSerializer, moduleIdentifierFactory);
    }

    SuppliedComponentMetadataSerializer createSuppliedComponentMetadataSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer) {
        ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        return new SuppliedComponentMetadataSerializer(moduleVersionIdentifierSerializer, attributeContainerSerializer);
    }

    ComponentMetadataRuleExecutor createComponentMetadataRuleExecutor(
        ValueSnapshotter valueSnapshotter,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        InMemoryCacheDecoratorFactory cacheDecoratorFactory,
        BuildCommencedTimeProvider timeProvider,
        ModuleComponentResolveMetadataSerializer serializer
    ) {
        return new ComponentMetadataRuleExecutor(cacheBuilderFactory, cacheDecoratorFactory, valueSnapshotter, timeProvider, serializer);
    }

    ComponentMetadataSupplierRuleExecutor createComponentMetadataSupplierRuleExecutor(
        ValueSnapshotter snapshotter,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        InMemoryCacheDecoratorFactory cacheDecoratorFactory,
        final BuildCommencedTimeProvider timeProvider,
        SuppliedComponentMetadataSerializer suppliedComponentMetadataSerializer,
        ListenerManager listenerManager
    ) {
        if (cacheDecoratorFactory instanceof CleaningInMemoryCacheDecoratorFactory) {
            listenerManager.addListener(new BuildModelLifecycleListener() {
                @Override
                public void beforeModelDiscarded(GradleInternal model, boolean buildFailed) {
                    ((CleaningInMemoryCacheDecoratorFactory) cacheDecoratorFactory).clearCaches(ComponentMetadataRuleExecutor::isMetadataRuleExecutorCache);
                }
            });
        }
        return new ComponentMetadataSupplierRuleExecutor(cacheBuilderFactory, cacheDecoratorFactory, snapshotter, timeProvider, suppliedComponentMetadataSerializer);
    }

    SignatureVerificationServiceFactory createSignatureVerificationServiceFactory(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        InMemoryCacheDecoratorFactory decoratorFactory,
        RepositoryTransportFactory transportFactory,
        BuildOperationExecutor buildOperationExecutor,
        BuildCommencedTimeProvider timeProvider,
        BuildScopedCacheBuilderFactory buildScopedCacheBuilderFactory,
        FileHasher fileHasher,
        StartParameter startParameter,
        ListenerManager listenerManager
    ) {
        return new DefaultSignatureVerificationServiceFactory(transportFactory, cacheBuilderFactory, decoratorFactory, buildOperationExecutor, fileHasher, buildScopedCacheBuilderFactory, timeProvider, startParameter.isRefreshKeys(), listenerManager.getBroadcaster(FileResourceListener.class));
    }

    private void registerBuildFinishedHooks(ListenerManager listenerManager, DependencyVerificationOverride dependencyVerificationOverride) {
        listenerManager.addListener(new BuildModelLifecycleListener() {
            @Override
            public void beforeModelDiscarded(GradleInternal model, boolean buildFailed) {
                dependencyVerificationOverride.buildFinished(model);
            }
        });
    }

    DependenciesAccessors createDependenciesAccessorGenerator(
        BuildTreeObjectFactory objectFactory,
        ClassPathRegistry registry,
        DependenciesAccessorsWorkspaceProvider workspace,
        DefaultProjectDependencyFactory factory,
        ExecutionEngine executionEngine,
        FeatureFlags featureFlags,
        FileCollectionFactory fileCollectionFactory,
        ImmutableAttributesFactory attributesFactory,
        CapabilityNotationParser capabilityNotationParser,
        InputFingerprinter inputFingerprinter
    ) {
        return objectFactory.newInstance(DefaultDependenciesAccessors.class, registry, workspace, factory, featureFlags, executionEngine, fileCollectionFactory, inputFingerprinter, attributesFactory, capabilityNotationParser);
    }


    /**
     * Execution engine for usage above Gradle scope
     *
     * Currently used for running artifact transforms in buildscript blocks.
     */
    ExecutionEngine createExecutionEngine(
        BuildInvocationScopeId buildInvocationScopeId,
        BuildOperationExecutor buildOperationExecutor,
        CurrentBuildOperationRef currentBuildOperationRef,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        Deleter deleter,
        ExecutionStateChangeDetector changeDetector,
        ListenerManager listenerManager,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        TimeoutHandler timeoutHandler,
        ValidateStep.ValidationWarningRecorder validationWarningRecorder,
        VirtualFileSystem virtualFileSystem,
        Problems problems
    ) {
        OutputChangeListener outputChangeListener = listenerManager.getBroadcaster(OutputChangeListener.class);
        // @formatter:off
        return new DefaultExecutionEngine(
            problems, new IdentifyStep<>(buildOperationExecutor,
            new IdentityCacheStep<>(
            new AssignWorkspaceStep<>(
            new LoadPreviousExecutionStateStep<>(
            new RemoveUntrackedExecutionStateStep<>(
            new CaptureStateBeforeExecutionStep<>(buildOperationExecutor, classLoaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
            new ValidateStep<>(virtualFileSystem, validationWarningRecorder, problems,
            new NoOpCachingStateStep<>(
            new ResolveChangesStep<>(changeDetector,
            new SkipUpToDateStep<>(
            new StoreExecutionStateStep<>(
            new ResolveInputChangesStep<>(
            new CaptureStateAfterExecutionStep<>(buildOperationExecutor, buildInvocationScopeId.getId(), outputSnapshotter, outputChangeListener,
            new CreateOutputsStep<>(
            new TimeoutStep<>(timeoutHandler, currentBuildOperationRef,
            new RemovePreviousOutputsStep<>(deleter, outputChangeListener,
            new ExecuteStep<>(buildOperationExecutor
        ))))))))))))))))));
        // @formatter:on
    }

    private static class NoOpCachingStateStep<C extends ValidationFinishedContext> implements Step<C, CachingResult> {
        private final Step<? super CachingContext, ? extends UpToDateResult> delegate;

        public NoOpCachingStateStep(Step<? super CachingContext, ? extends UpToDateResult> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CachingResult execute(UnitOfWork work, ValidationFinishedContext context) {
            UpToDateResult result = delegate.execute(work, new CachingContext(context, CachingState.NOT_DETERMINED));
            return new CachingResult(result, CachingState.NOT_DETERMINED);
        }
    }
}
