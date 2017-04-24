/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.service.scopes;

import com.google.common.hash.HashCode;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.cache.DefaultGeneratedGradleJarCache;
import org.gradle.api.internal.cache.GeneratedGradleJarCache;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.AbiExtractingClasspathContentHasher;
import org.gradle.api.internal.changedetection.state.BuildScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CachingClasspathEntryHasher;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.ClasspathEntryHasher;
import org.gradle.api.internal.changedetection.state.ClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.CompileClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultClasspathContentHasher;
import org.gradle.api.internal.changedetection.state.DefaultClasspathEntryHasher;
import org.gradle.api.internal.changedetection.state.DefaultClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultCompileClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultGenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.DefaultFileHasher;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.project.BuildOperationProjectConfigurator;
import org.gradle.api.internal.project.ProjectConfigurator;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.DefaultAsyncWorkTracker;
import org.gradle.internal.work.DefaultWorkerLeaseService;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider;
import org.gradle.util.GradleVersion;

import java.io.File;

/**
 * Contains the services for a single build session, which could be a single build or multiple builds when in continuous mode.
 */
public class BuildSessionScopeServices extends DefaultServiceRegistry {

    public BuildSessionScopeServices(final ServiceRegistry parent, final StartParameter startParameter, ClassPath injectedPluginClassPath) {
        super(parent);
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration registration) {
                add(StartParameter.class, startParameter);
                for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerBuildSessionServices(registration);
                }
            }
        });
        add(InjectedPluginClasspath.class, new InjectedPluginClasspath(injectedPluginClassPath));
        addProvider(new CacheRepositoryServices(startParameter.getGradleUserHomeDir(), startParameter.getProjectCacheDir()));
    }

    ListenerManager createListenerManager(ListenerManager parent) {
        return parent.createChild();
    }

    DeploymentRegistry createDeploymentRegistry() {
        return new DefaultDeploymentRegistry();
    }

    BuildOperationExecutor createBuildOperationExecutor(ListenerManager listenerManager, TimeProvider timeProvider, ProgressLoggerFactory progressLoggerFactory, WorkerLeaseService workerLeaseService, StartParameter startParameter, ExecutorFactory executorFactory) {
        return new DefaultBuildOperationExecutor(listenerManager.getBroadcaster(BuildOperationListener.class), timeProvider, progressLoggerFactory, new DefaultBuildOperationQueueFactory(workerLeaseService), executorFactory, startParameter.getMaxWorkerCount());
    }

    WorkerProcessFactory createWorkerProcessFactory(StartParameter startParameter, MessagingServer messagingServer, ClassPathRegistry classPathRegistry,
                                                    TemporaryFileProvider temporaryFileProvider, JavaExecHandleFactory execHandleFactory, JvmVersionDetector jvmVersionDetector,
                                                    MemoryManager memoryManager) {
        return new DefaultWorkerProcessFactory(
            startParameter.getLogLevel(),
            messagingServer,
            classPathRegistry,
            new LongIdGenerator(),
            startParameter.getGradleUserHomeDir(),
            temporaryFileProvider,
            execHandleFactory,
            jvmVersionDetector,
            get(OutputEventListener.class),
            memoryManager
        );
    }

    ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry(
                new DefaultClassPathProvider(get(ModuleRegistry.class)),
                get(WorkerProcessClassPathProvider.class)
        );
    }

    WorkerProcessClassPathProvider createWorkerProcessClassPathProvider(CacheRepository cacheRepository) {
        return new WorkerProcessClassPathProvider(cacheRepository);
    }

    GeneratedGradleJarCache createGeneratedGradleJarCache(CacheRepository cacheRepository) {
        String gradleVersion = GradleVersion.current().getVersion();
        return new DefaultGeneratedGradleJarCache(cacheRepository, gradleVersion);
    }

    ProjectConfigurator createProjectConfigurator(BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperationProjectConfigurator(buildOperationExecutor);
    }

    static class CacheLayout {
        final File cacheDir;

        public CacheLayout(File cacheDir) {
            this.cacheDir = cacheDir;
        }
    }

    CacheLayout createCacheLayout(StartParameter startParameter) {
        BuildLayout buildLayout = new BuildLayoutFactory().getLayoutFor(new BuildLayoutConfiguration(startParameter));
        File cacheDir = startParameter.getProjectCacheDir() != null ? startParameter.getProjectCacheDir() : new File(buildLayout.getRootDirectory(), ".gradle");
        return new CacheLayout(cacheDir);
    }

    BuildScopeFileTimeStampInspector createFileTimeStampInspector(CacheLayout cacheLayout, CacheScopeMapping cacheScopeMapping, ListenerManager listenerManager) {
        File workDir = cacheScopeMapping.getBaseDirectory(cacheLayout.cacheDir, "fileChanges", VersionStrategy.CachePerVersion);
        BuildScopeFileTimeStampInspector timeStampInspector = new BuildScopeFileTimeStampInspector(workDir);
        listenerManager.addListener(timeStampInspector);
        return timeStampInspector;
    }

    CrossBuildFileHashCache createCrossBuildFileHashCache(CacheLayout cacheLayout, CacheScopeMapping cacheScopeMapping, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        File cacheDir = cacheScopeMapping.getBaseDirectory(cacheLayout.cacheDir, "fileHashes", VersionStrategy.CachePerVersion);
        return new CrossBuildFileHashCache(cacheDir, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    FileHasher createFileSnapshotter(TaskHistoryStore cacheAccess, StringInterner stringInterner, FileSystem fileSystem, BuildScopeFileTimeStampInspector fileTimeStampInspector) {
        return new CachingFileHasher(new DefaultFileHasher(), cacheAccess, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
    }

    FileSystemSnapshotter createFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        return new DefaultFileSystemSnapshotter(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror);
    }

    GenericFileCollectionSnapshotter createGenericFileCollectionSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        return new DefaultGenericFileCollectionSnapshotter(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
    }

    ClasspathSnapshotter createClasspathSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, ClasspathEntryHasher classpathEntryHasher, FileSystemSnapshotter fileSystemSnapshotter) {
        return new DefaultClasspathSnapshotter(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter, classpathEntryHasher);
    }

    CompileClasspathSnapshotter createCompileClasspathSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, TaskHistoryStore store, FileSystemSnapshotter fileSystemSnapshotter) {
        PersistentIndexedCache<HashCode, HashCode> signatureCache = store.createCache("jvmClassSignatures", HashCode.class, new HashCodeSerializer(), 400000, true);
        ClasspathEntryHasher classpathEntryHasher = new CachingClasspathEntryHasher(new DefaultClasspathEntryHasher(new AbiExtractingClasspathContentHasher(new DefaultClasspathContentHasher())), signatureCache);
        return new DefaultCompileClasspathSnapshotter(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter, classpathEntryHasher);
    }

    ImmutableAttributesFactory createImmutableAttributesFactory() {
        return new DefaultImmutableAttributesFactory();
    }

    ResourceLockCoordinationService createWorkerLeaseCoordinationService() {
        return new DefaultResourceLockCoordinationService();
    }

    AsyncWorkTracker createAsyncWorkTracker(ProjectLeaseRegistry projectLeaseRegistry) {
        return new DefaultAsyncWorkTracker(projectLeaseRegistry);
    }

    WorkerLeaseService createWorkerLeaseService(ResourceLockCoordinationService coordinationService, StartParameter startParameter) {
        return new DefaultWorkerLeaseService(coordinationService, startParameter.isParallelProjectExecutionEnabled(), startParameter.getMaxWorkerCount());
    }
}
