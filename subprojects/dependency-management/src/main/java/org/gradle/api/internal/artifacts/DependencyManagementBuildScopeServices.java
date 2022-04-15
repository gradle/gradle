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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.component.DefaultComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConnectionFailureRepositoryDisabler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashCodec;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryDisabler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CachingVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.AbstractModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.InMemoryModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleComponentResolveMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCacheProvider;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleSourcesSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.PersistentModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ReadOnlyModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.SuppliedComponentMetadataSerializer;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.TwoStageModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.DefaultModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.DefaultModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ReadOnlyModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ReadOnlyModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.TwoStageArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.TwoStageModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.InMemoryModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.ReadOnlyModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.TwoStageModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.LocalComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DefaultArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DesugaredAttributeContainerSerializer;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations;
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSourceCodec;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataFileSource;
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.transform.TransformationNodeDependencyResolver;
import org.gradle.api.internal.artifacts.verification.signatures.DefaultSignatureVerificationServiceFactory;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.catalog.DefaultDependenciesAccessors;
import org.gradle.api.internal.catalog.DependenciesAccessorsWorkspaceProvider;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore;
import org.gradle.api.internal.filestore.TwoStageArtifactIdentifierFileStore;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.notations.ClientModuleNotationParserFactory;
import org.gradle.api.internal.notations.DependencyConstraintNotationParser;
import org.gradle.api.internal.notations.DependencyNotationParser;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.internal.CleaningInMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.GeneratedGradleJarCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.cache.scopes.BuildScopedCache;
import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.Try;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.PreviousExecutionState;
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
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.instantiation.InstantiatorFactory;
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
import org.gradle.internal.resource.cached.ByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.cached.TwoStageByUrlCachedExternalResourceIndex;
import org.gradle.internal.resource.cached.TwoStageExternalResourceFileStore;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.local.ivy.LocallyAvailableResourceFinderFactory;
import org.gradle.internal.resource.transfer.CachingTextUriResourceLoader;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.gradle.util.internal.SimpleMapInterner;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The set of dependency management services that are created per build.
 */
class DependencyManagementBuildScopeServices {
    void configure(ServiceRegistration registration) {
        registration.add(ProjectArtifactResolver.class);
        registration.add(ProjectArtifactSetResolver.class);
        registration.add(ProjectDependencyResolver.class);
        registration.add(DefaultExternalResourceFileStore.Factory.class);
        registration.add(DefaultArtifactIdentifierFileStore.Factory.class);
        registration.add(TransformationNodeDependencyResolver.class);
    }

    DependencyResolutionManagementInternal createSharedDependencyResolutionServices(Instantiator instantiator,
                                                                                    UserCodeApplicationContext context,
                                                                                    DependencyManagementServices dependencyManagementServices,
                                                                                    FileResolver fileResolver,
                                                                                    FileCollectionFactory fileCollectionFactory,
                                                                                    DependencyMetaDataProvider dependencyMetaDataProvider,
                                                                                    ObjectFactory objects,
                                                                                    ProviderFactory providers,
                                                                                    CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        return instantiator.newInstance(DefaultDependencyResolutionManagement.class,
            context,
            dependencyManagementServices,
            fileResolver,
            fileCollectionFactory,
            dependencyMetaDataProvider,
            objects,
            providers,
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

    DefaultProjectDependencyFactory createProjectDependencyFactory(
        Instantiator instantiator,
        StartParameter startParameter,
        ImmutableAttributesFactory attributesFactory) {
        NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        return new DefaultProjectDependencyFactory(instantiator, startParameter.isBuildProjectDependencies(), capabilityNotationParser, attributesFactory);
    }

    DependencyFactory createDependencyFactory(
        Instantiator instantiator,
        DefaultProjectDependencyFactory factory,
        ClassPathRegistry classPathRegistry,
        CurrentGradleInstallation currentGradleInstallation,
        FileCollectionFactory fileCollectionFactory,
        RuntimeShadedJarFactory runtimeShadedJarFactory,
        ImmutableAttributesFactory attributesFactory,
        SimpleMapInterner stringInterner) {
        NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory(factory);

        return new DefaultDependencyFactory(
            DependencyNotationParser.parser(instantiator, factory, classPathRegistry, fileCollectionFactory, runtimeShadedJarFactory, currentGradleInstallation, stringInterner, attributesFactory, capabilityNotationParser),
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

    MavenMutableModuleMetadataFactory createMutableMavenMetadataFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                                        ImmutableAttributesFactory attributesFactory,
                                                                        NamedObjectInstantiator instantiator,
                                                                        PreferJavaRuntimeVariant schema) {
        return new MavenMutableModuleMetadataFactory(moduleIdentifierFactory, attributesFactory, instantiator, schema);
    }

    IvyMutableModuleMetadataFactory createMutableIvyMetadataFactory(ImmutableModuleIdentifierFactory moduleIdentifierFactory, ImmutableAttributesFactory attributesFactory, PreferJavaRuntimeVariant schema) {
        return new IvyMutableModuleMetadataFactory(moduleIdentifierFactory, attributesFactory, schema);
    }

    AttributeContainerSerializer createAttributeContainerSerializer(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator) {
        return new DesugaredAttributeContainerSerializer(attributesFactory, instantiator);
    }

    ModuleSourcesSerializer createModuleSourcesSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileStoreAndIndexProvider fileStoreAndIndexProvider) {
        Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> codecs = ImmutableMap.of(
            MetadataFileSource.CODEC_ID, new DefaultMetadataFileSourceCodec(moduleIdentifierFactory, fileStoreAndIndexProvider.getArtifactIdentifierFileStore()),
            ModuleDescriptorHashModuleSource.CODEC_ID, new ModuleDescriptorHashCodec()
        );
        return new ModuleSourcesSerializer(codecs);
    }

    ModuleRepositoryCacheProvider createModuleRepositoryCacheProvider(BuildCommencedTimeProvider timeProvider,
                                                                      ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                                      ArtifactCachesProvider artifactCaches,
                                                                      AttributeContainerSerializer attributeContainerSerializer,
                                                                      MavenMutableModuleMetadataFactory mavenMetadataFactory,
                                                                      IvyMutableModuleMetadataFactory ivyMetadataFactory,
                                                                      SimpleMapInterner stringInterner,
                                                                      FileStoreAndIndexProvider fileStoreAndIndexProvider,
                                                                      ModuleSourcesSerializer moduleSourcesSerializer,
                                                                      ChecksumService checksumService) {
        ArtifactIdentifierFileStore artifactIdentifierFileStore = fileStoreAndIndexProvider.getArtifactIdentifierFileStore();
        ModuleRepositoryCaches writableCaches = artifactCaches.withWritableCache((md, manager) -> prepareModuleRepositoryCaches(md, manager, timeProvider, moduleIdentifierFactory, attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, stringInterner, artifactIdentifierFileStore, moduleSourcesSerializer, checksumService));
        AtomicReference<Path> roCachePath = new AtomicReference<>();
        Optional<ModuleRepositoryCaches> readOnlyCaches = artifactCaches.withReadOnlyCache((ro, manager) -> {
            roCachePath.set(ro.getCacheDir().toPath());
            return prepareReadOnlyModuleRepositoryCaches(ro, manager, timeProvider, moduleIdentifierFactory, attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, stringInterner, artifactIdentifierFileStore, moduleSourcesSerializer, checksumService);
        });
        AbstractModuleVersionsCache moduleVersionsCache = readOnlyCaches.map(mrc -> (AbstractModuleVersionsCache) new TwoStageModuleVersionsCache(timeProvider, mrc.moduleVersionsCache, writableCaches.moduleVersionsCache)).orElse(writableCaches.moduleVersionsCache);
        AbstractModuleMetadataCache persistentModuleMetadataCache = readOnlyCaches.map(mrc -> (AbstractModuleMetadataCache) new TwoStageModuleMetadataCache(timeProvider, mrc.moduleMetadataCache, writableCaches.moduleMetadataCache)).orElse(writableCaches.moduleMetadataCache);
        AbstractArtifactsCache moduleArtifactsCache = readOnlyCaches.map(mrc -> (AbstractArtifactsCache) new TwoStageArtifactsCache(timeProvider, mrc.moduleArtifactsCache, writableCaches.moduleArtifactsCache)).orElse(writableCaches.moduleArtifactsCache);
        ModuleArtifactCache moduleArtifactCache = readOnlyCaches.map(mrc -> (ModuleArtifactCache) new TwoStageModuleArtifactCache(roCachePath.get(), mrc.moduleArtifactCache, writableCaches.moduleArtifactCache)).orElse(writableCaches.moduleArtifactCache);
        ModuleRepositoryCaches persistentCaches = new ModuleRepositoryCaches(
            new InMemoryModuleVersionsCache(timeProvider, moduleVersionsCache),
            new InMemoryModuleMetadataCache(timeProvider, persistentModuleMetadataCache),
            new InMemoryModuleArtifactsCache(timeProvider, moduleArtifactsCache),
            new InMemoryModuleArtifactCache(timeProvider, moduleArtifactCache)
        );
        ModuleRepositoryCaches inMemoryOnlyCaches = new ModuleRepositoryCaches(
            new InMemoryModuleVersionsCache(timeProvider),
            new InMemoryModuleMetadataCache(timeProvider),
            new InMemoryModuleArtifactsCache(timeProvider),
            new InMemoryModuleArtifactCache(timeProvider)
        );
        return new ModuleRepositoryCacheProvider(persistentCaches, inMemoryOnlyCaches);
    }

    private ModuleRepositoryCaches prepareModuleRepositoryCaches(ArtifactCacheMetadata artifactCacheMetadata, ArtifactCacheLockingManager artifactCacheLockingManager, BuildCommencedTimeProvider timeProvider, ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, SimpleMapInterner stringInterner, ArtifactIdentifierFileStore artifactIdentifierFileStore, ModuleSourcesSerializer moduleSourcesSerializer, ChecksumService checksumService) {
        DefaultModuleVersionsCache moduleVersionsCache = new DefaultModuleVersionsCache(
            timeProvider,
            artifactCacheLockingManager,
            moduleIdentifierFactory);
        PersistentModuleMetadataCache moduleMetadataCache = new PersistentModuleMetadataCache(
            timeProvider,
            artifactCacheLockingManager,
            artifactCacheMetadata,
            moduleIdentifierFactory,
            attributeContainerSerializer,
            mavenMetadataFactory,
            ivyMetadataFactory,
            stringInterner,
            moduleSourcesSerializer,
            checksumService);
        DefaultModuleArtifactsCache moduleArtifactsCache = new DefaultModuleArtifactsCache(
            timeProvider,
            artifactCacheLockingManager
        );
        DefaultModuleArtifactCache moduleArtifactCache = new DefaultModuleArtifactCache(
            "module-artifact",
            timeProvider,
            artifactCacheLockingManager,
            artifactIdentifierFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
        return new ModuleRepositoryCaches(
            moduleVersionsCache,
            moduleMetadataCache,
            moduleArtifactsCache,
            moduleArtifactCache
        );
    }

    private ModuleRepositoryCaches prepareReadOnlyModuleRepositoryCaches(ArtifactCacheMetadata artifactCacheMetadata, ArtifactCacheLockingManager artifactCacheLockingManager, BuildCommencedTimeProvider timeProvider, ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, SimpleMapInterner stringInterner, ArtifactIdentifierFileStore artifactIdentifierFileStore, ModuleSourcesSerializer moduleSourcesSerializer, ChecksumService checksumService) {
        ReadOnlyModuleVersionsCache moduleVersionsCache = new ReadOnlyModuleVersionsCache(
            timeProvider,
            artifactCacheLockingManager,
            moduleIdentifierFactory);
        ReadOnlyModuleMetadataCache moduleMetadataCache = new ReadOnlyModuleMetadataCache(
            timeProvider,
            artifactCacheLockingManager,
            artifactCacheMetadata,
            moduleIdentifierFactory,
            attributeContainerSerializer,
            mavenMetadataFactory,
            ivyMetadataFactory,
            stringInterner,
            moduleSourcesSerializer,
            checksumService);
        ReadOnlyModuleArtifactsCache moduleArtifactsCache = new ReadOnlyModuleArtifactsCache(
            timeProvider,
            artifactCacheLockingManager
        );
        ReadOnlyModuleArtifactCache moduleArtifactCache = new ReadOnlyModuleArtifactCache(
            "module-artifact",
            timeProvider,
            artifactCacheLockingManager,
            artifactIdentifierFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
        return new ModuleRepositoryCaches(
            moduleVersionsCache,
            moduleMetadataCache,
            moduleArtifactsCache,
            moduleArtifactCache
        );
    }

    FileStoreAndIndexProvider createFileStoreAndIndexProvider(
        BuildCommencedTimeProvider timeProvider,
        ArtifactCachesProvider artifactCaches,
        DefaultExternalResourceFileStore.Factory defaultExternalResourceFileStoreFactory,
        DefaultArtifactIdentifierFileStore.Factory defaultArtifactIdentifierFileStoreFactory
    ) {
        ExternalResourceFileStore writableFileStore = defaultExternalResourceFileStoreFactory.create(artifactCaches.getWritableCacheMetadata());
        ExternalResourceFileStore externalResourceFileStore = artifactCaches.withReadOnlyCache((md, manager) ->
            (ExternalResourceFileStore) new TwoStageExternalResourceFileStore(defaultExternalResourceFileStoreFactory.create(md), writableFileStore)).orElse(writableFileStore);
        CachedExternalResourceIndex<String> writableByUrlCachedExternalResourceIndex = prepareArtifactUrlCachedResolutionIndex(timeProvider, artifactCaches.getWritableCacheLockingManager(), externalResourceFileStore, artifactCaches.getWritableCacheMetadata());
        ArtifactIdentifierFileStore writableArtifactIdentifierFileStore = artifactCaches.withWritableCache((md, manager) -> defaultArtifactIdentifierFileStoreFactory.create(md));
        ArtifactIdentifierFileStore artifactIdentifierFileStore = artifactCaches.withReadOnlyCache((md, manager) -> (ArtifactIdentifierFileStore) new TwoStageArtifactIdentifierFileStore(
            defaultArtifactIdentifierFileStoreFactory.create(md),
            writableArtifactIdentifierFileStore
        )).orElse(writableArtifactIdentifierFileStore);
        return new FileStoreAndIndexProvider(
            artifactCaches.withReadOnlyCache((md, manager) -> (CachedExternalResourceIndex<String>) new TwoStageByUrlCachedExternalResourceIndex(md.getCacheDir().toPath(), prepareArtifactUrlCachedResolutionIndex(timeProvider, manager, externalResourceFileStore, md), writableByUrlCachedExternalResourceIndex)).orElse(writableByUrlCachedExternalResourceIndex),
            externalResourceFileStore, artifactIdentifierFileStore);
    }

    private ByUrlCachedExternalResourceIndex prepareArtifactUrlCachedResolutionIndex(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager, ExternalResourceFileStore externalResourceFileStore, ArtifactCacheMetadata artifactCacheMetadata) {
        return new ByUrlCachedExternalResourceIndex(
            "resource-at-url",
            timeProvider,
            artifactCacheLockingManager,
            externalResourceFileStore.getFileAccessTracker(),
            artifactCacheMetadata.getCacheDir().toPath()
        );
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

    LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> createArtifactRevisionIdLocallyAvailableResourceFinder(ArtifactCachesProvider artifactCaches,
                                                                                                                           LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                                                                                                           FileStoreAndIndexProvider fileStoreAndIndexProvider,
                                                                                                                           ChecksumService checksumService) {
        LocallyAvailableResourceFinderFactory finderFactory = new LocallyAvailableResourceFinderFactory(
            artifactCaches,
            localMavenRepositoryLocator,
            fileStoreAndIndexProvider.getArtifactIdentifierFileStore(), checksumService);
        return finderFactory.create();
    }

    RepositoryTransportFactory createRepositoryTransportFactory(TemporaryFileProvider temporaryFileProvider,
                                                                FileStoreAndIndexProvider fileStoreAndIndexProvider,
                                                                BuildCommencedTimeProvider buildCommencedTimeProvider,
                                                                ArtifactCachesProvider artifactCachesProvider,
                                                                List<ResourceConnectorFactory> resourceConnectorFactories,
                                                                BuildOperationExecutor buildOperationExecutor,
                                                                ProducerGuard<ExternalResourceName> producerGuard,
                                                                FileResourceRepository fileResourceRepository,
                                                                ChecksumService checksumService,
                                                                StartParameterResolutionOverride startParameterResolutionOverride,
                                                                ListenerManager listenerManager) {
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
            checksumService,
            listenerManager.getBroadcaster(FileResourceListener.class)));
    }

    RepositoryDisabler createRepositoryDisabler() {
        return new ConnectionFailureRepositoryDisabler();
    }

    DependencyVerificationOverride createDependencyVerificationOverride(StartParameterResolutionOverride startParameterResolutionOverride,
                                                                        BuildOperationExecutor buildOperationExecutor,
                                                                        ChecksumService checksumService,
                                                                        SignatureVerificationServiceFactory signatureVerificationServiceFactory,
                                                                        DocumentationRegistry documentationRegistry,
                                                                        ListenerManager listenerManager,
                                                                        BuildCommencedTimeProvider timeProvider,
                                                                        ServiceRegistry serviceRegistry) {
        DependencyVerificationOverride override = startParameterResolutionOverride.dependencyVerificationOverride(buildOperationExecutor, checksumService, signatureVerificationServiceFactory, documentationRegistry, timeProvider, () -> serviceRegistry.get(GradleProperties.class));
        registerBuildFinishedHooks(listenerManager, override);
        return override;
    }

    ResolveIvyFactory createResolveIvyFactory(StartParameterResolutionOverride startParameterResolutionOverride,
                                              ModuleRepositoryCacheProvider moduleRepositoryCacheProvider,
                                              DependencyVerificationOverride dependencyVerificationOverride,
                                              BuildCommencedTimeProvider buildCommencedTimeProvider,
                                              VersionComparator versionComparator,
                                              ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                              RepositoryDisabler repositoryBlacklister,
                                              VersionParser versionParser,
                                              ListenerManager listenerManager,
                                              CalculatedValueContainerFactory calculatedValueContainerFactory) {
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
            calculatedValueContainerFactory);
    }

    ArtifactDependencyResolver createArtifactDependencyResolver(ResolveIvyFactory resolveIvyFactory,
                                                                DependencyDescriptorFactory dependencyDescriptorFactory,
                                                                VersionComparator versionComparator,
                                                                List<ResolverProviderFactory> resolverFactories,
                                                                ProjectDependencyResolver projectDependencyResolver,
                                                                ModuleExclusions moduleExclusions,
                                                                BuildOperationExecutor buildOperationExecutor,
                                                                ComponentSelectorConverter componentSelectorConverter,
                                                                ImmutableAttributesFactory attributesFactory,
                                                                VersionSelectorScheme versionSelectorScheme,
                                                                VersionParser versionParser,
                                                                ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
                                                                InstantiatorFactory instantiatorFactory,
                                                                ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
                                                                CalculatedValueContainerFactory calculatedValueContainerFactory) {
        return new DefaultArtifactDependencyResolver(
            buildOperationExecutor,
            resolverFactories,
            projectDependencyResolver,
            resolveIvyFactory,
            dependencyDescriptorFactory,
            versionComparator,
            moduleExclusions,
            componentSelectorConverter,
            attributesFactory,
            versionSelectorScheme,
            versionParser,
            componentMetadataSupplierRuleExecutor,
            instantiatorFactory,
            componentSelectionDescriptorFactory,
            calculatedValueContainerFactory);
    }

    ProjectPublicationRegistry createProjectPublicationRegistry() {
        return new DefaultProjectPublicationRegistry();
    }

    LocalComponentProvider createProjectComponentProvider(
        LocalComponentMetadataBuilder metaDataBuilder,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory
    ) {
        return new DefaultProjectLocalComponentProvider(metaDataBuilder, moduleIdentifierFactory);
    }

    ComponentSelectorConverter createModuleVersionSelectorFactory(ComponentIdentifierFactory componentIdentifierFactory, LocalComponentRegistry localComponentRegistry) {
        return new DefaultComponentSelectorConverter(componentIdentifierFactory, localComponentRegistry);
    }

    VersionParser createVersionParser() {
        return new VersionParser();
    }

    VersionSelectorScheme createVersionSelectorScheme(VersionComparator versionComparator, VersionParser versionParser) {
        DefaultVersionSelectorScheme delegate = new DefaultVersionSelectorScheme(versionComparator, versionParser);
        CachingVersionSelectorScheme selectorScheme = new CachingVersionSelectorScheme(delegate);
        return selectorScheme;
    }

    SimpleMapInterner createStringInterner() {
        return SimpleMapInterner.threadSafe();
    }

    ModuleComponentResolveMetadataSerializer createModuleComponentResolveMetadataSerializer(ImmutableAttributesFactory attributesFactory, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, NamedObjectInstantiator instantiator, ModuleSourcesSerializer moduleSourcesSerializer) {
        DesugaringAttributeContainerSerializer attributeContainerSerializer = new DesugaringAttributeContainerSerializer(attributesFactory, instantiator);
        return new ModuleComponentResolveMetadataSerializer(new ModuleMetadataSerializer(attributeContainerSerializer, mavenMetadataFactory, ivyMetadataFactory, moduleSourcesSerializer), attributeContainerSerializer, moduleIdentifierFactory);
    }

    SuppliedComponentMetadataSerializer createSuppliedComponentMetadataSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer) {
        ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        return new SuppliedComponentMetadataSerializer(moduleVersionIdentifierSerializer, attributeContainerSerializer);
    }

    ComponentMetadataRuleExecutor createComponentMetadataRuleExecutor(ValueSnapshotter valueSnapshotter,
                                                                      GlobalScopedCache globalScopedCache,
                                                                      InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                                      BuildCommencedTimeProvider timeProvider,
                                                                      ModuleComponentResolveMetadataSerializer serializer) {
        return new ComponentMetadataRuleExecutor(globalScopedCache, cacheDecoratorFactory, valueSnapshotter, timeProvider, serializer);
    }

    ComponentMetadataSupplierRuleExecutor createComponentMetadataSupplierRuleExecutor(ValueSnapshotter snapshotter,
                                                                                      GlobalScopedCache globalScopedCache,
                                                                                      InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                                                      final BuildCommencedTimeProvider timeProvider,
                                                                                      SuppliedComponentMetadataSerializer suppliedComponentMetadataSerializer,
                                                                                      ListenerManager listenerManager) {
        if (cacheDecoratorFactory instanceof CleaningInMemoryCacheDecoratorFactory) {
            listenerManager.addListener(new InternalBuildFinishedListener() {
                @Override
                public void buildFinished(GradleInternal build, boolean failed) {
                    ((CleaningInMemoryCacheDecoratorFactory) cacheDecoratorFactory).clearCaches(ComponentMetadataRuleExecutor::isMetadataRuleExecutorCache);
                }
            });
        }
        return new ComponentMetadataSupplierRuleExecutor(globalScopedCache, cacheDecoratorFactory, snapshotter, timeProvider, suppliedComponentMetadataSerializer);
    }

    SignatureVerificationServiceFactory createSignatureVerificationServiceFactory(GlobalScopedCache globalScopedCache,
                                                                                  InMemoryCacheDecoratorFactory decoratorFactory,
                                                                                  RepositoryTransportFactory transportFactory,
                                                                                  BuildOperationExecutor buildOperationExecutor,
                                                                                  BuildCommencedTimeProvider timeProvider,
                                                                                  BuildScopedCache buildScopedCache,
                                                                                  FileHasher fileHasher,
                                                                                  StartParameter startParameter) {
        return new DefaultSignatureVerificationServiceFactory(transportFactory, globalScopedCache, decoratorFactory, buildOperationExecutor, fileHasher, buildScopedCache, timeProvider, startParameter.isRefreshKeys());
    }

    private void registerBuildFinishedHooks(ListenerManager listenerManager, DependencyVerificationOverride dependencyVerificationOverride) {
        listenerManager.addListener(new InternalBuildFinishedListener() {
            @Override
            public void buildFinished(GradleInternal build, boolean failed) {
                dependencyVerificationOverride.buildFinished(build);
            }
        });
    }

    DependenciesAccessors createDependenciesAccessorGenerator(ClassPathRegistry registry,
                                                              DependenciesAccessorsWorkspaceProvider workspace,
                                                              DefaultProjectDependencyFactory factory,
                                                              ExecutionEngine executionEngine,
                                                              FeaturePreviews featurePreviews,
                                                              FileCollectionFactory fileCollectionFactory,
                                                              InputFingerprinter inputFingerprinter) {
        return new DefaultDependenciesAccessors(registry, workspace, factory, featurePreviews, executionEngine, fileCollectionFactory, inputFingerprinter);
    }


    /**
     * Execution engine for usage above Gradle scope
     *
     * Currently used for running artifact transformations in buildscript blocks.
     */
    ExecutionEngine createExecutionEngine(
        BuildOperationExecutor buildOperationExecutor,
        CurrentBuildOperationRef currentBuildOperationRef,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        Deleter deleter,
        ExecutionStateChangeDetector changeDetector,
        InputFingerprinter inputFingerprinter,
        ListenerManager listenerManager,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        TimeoutHandler timeoutHandler,
        ValidateStep.ValidationWarningRecorder validationWarningRecorder,
        VirtualFileSystem virtualFileSystem,
        DocumentationRegistry documentationRegistry
    ) {
        OutputChangeListener outputChangeListener = listenerManager.getBroadcaster(OutputChangeListener.class);
        // TODO: Figure out how to get rid of origin scope id in snapshot outputs step
        UniqueId fixedUniqueId = UniqueId.from("dhwwyv4tqrd43cbxmdsf24wquu");
        // @formatter:off
        return new DefaultExecutionEngine(documentationRegistry,
            new IdentifyStep<>(
            new IdentityCacheStep<>(
            new AssignWorkspaceStep<>(
            new LoadPreviousExecutionStateStep<>(
            new RemoveUntrackedExecutionStateStep<>(
            new CaptureStateBeforeExecutionStep<>(buildOperationExecutor, classLoaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
            new ValidateStep<>(virtualFileSystem, validationWarningRecorder,
            new NoOpCachingStateStep<>(
            new ResolveChangesStep<>(changeDetector,
            new SkipUpToDateStep<>(
            new StoreExecutionStateStep<>(
            new ResolveInputChangesStep<>(
            new CaptureStateAfterExecutionStep<>(buildOperationExecutor, fixedUniqueId, outputSnapshotter, outputChangeListener,
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
            UpToDateResult result = delegate.execute(work, new CachingContext() {
                @Override
                public CachingState getCachingState() {
                    return CachingState.NOT_DETERMINED;
                }

                @Override
                public Optional<String> getNonIncrementalReason() {
                    return context.getNonIncrementalReason();
                }

                @Override
                public WorkValidationContext getValidationContext() {
                    return context.getValidationContext();
                }

                @Override
                public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                    return context.getInputProperties();
                }

                @Override
                public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                    return context.getInputFileProperties();
                }

                @Override
                public UnitOfWork.Identity getIdentity() {
                    return context.getIdentity();
                }

                @Override
                public File getWorkspace() {
                    return context.getWorkspace();
                }

                @Override
                public Optional<ExecutionHistoryStore> getHistory() {
                    return context.getHistory();
                }

                @Override
                public Optional<PreviousExecutionState> getPreviousExecutionState() {
                    return context.getPreviousExecutionState();
                }

                @Override
                public Optional<ValidationResult> getValidationProblems() {
                    return context.getValidationProblems();
                }

                @Override
                public Optional<BeforeExecutionState> getBeforeExecutionState() {
                    return context.getBeforeExecutionState();
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
                public Optional<AfterExecutionState> getAfterExecutionState() {
                    return result.getAfterExecutionState();
                }

                @Override
                public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                    return result.getReusedOutputOriginMetadata();
                }

                @Override
                public Try<ExecutionResult> getExecutionResult() {
                    return result.getExecutionResult();
                }

                @Override
                public Duration getDuration() {
                    return result.getDuration();
                }
            };
        }
    }
}
