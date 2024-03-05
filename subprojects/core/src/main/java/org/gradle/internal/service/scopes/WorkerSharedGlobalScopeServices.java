/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.provider.DefaultPropertyFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.initialization.DefaultLegacyTypesSupport;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.impl.DefaultDeleter;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.BuildOperationState;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.DefaultBuildOperationExecutor;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationRunner;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationProgressDetails;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;

import javax.annotation.Nullable;

import static org.gradle.api.internal.file.ManagedFactories.DirectoryManagedFactory;
import static org.gradle.api.internal.file.ManagedFactories.DirectoryPropertyManagedFactory;
import static org.gradle.api.internal.file.ManagedFactories.RegularFileManagedFactory;
import static org.gradle.api.internal.file.ManagedFactories.RegularFilePropertyManagedFactory;
import static org.gradle.api.internal.file.collections.ManagedFactories.ConfigurableFileCollectionManagedFactory;
import static org.gradle.api.internal.provider.ManagedFactories.ListPropertyManagedFactory;
import static org.gradle.api.internal.provider.ManagedFactories.MapPropertyManagedFactory;
import static org.gradle.api.internal.provider.ManagedFactories.PropertyManagedFactory;
import static org.gradle.api.internal.provider.ManagedFactories.ProviderManagedFactory;
import static org.gradle.api.internal.provider.ManagedFactories.SetPropertyManagedFactory;

public class WorkerSharedGlobalScopeServices extends BasicGlobalScopeServices {

    protected final ClassPath additionalModuleClassPath;

    public WorkerSharedGlobalScopeServices(ClassPath additionalModuleClassPath) {
        this.additionalModuleClassPath = additionalModuleClassPath;
    }

    protected CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory, ProgressLoggerFactory progressLoggerFactory) {
        return new DefaultCacheFactory(fileLockManager, executorFactory, progressLoggerFactory);
    }

    LegacyTypesSupport createLegacyTypesSupport() {
        return new DefaultLegacyTypesSupport();
    }

    BuildOperationIdFactory createBuildOperationIdProvider() {
        return new DefaultBuildOperationIdFactory();
    }

    ProgressLoggerFactory createProgressLoggerFactory(OutputEventListener outputEventListener, Clock clock, BuildOperationIdFactory buildOperationIdFactory) {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(outputEventListener), clock, buildOperationIdFactory);
    }

    Clock createClock() {
        return Time.clock();
    }

    CrossBuildInMemoryCacheFactory createCrossBuildInMemoryCacheFactory(ListenerManager listenerManager) {
        return new DefaultCrossBuildInMemoryCacheFactory(listenerManager);
    }

    NamedObjectInstantiator createNamedObjectInstantiator(CrossBuildInMemoryCacheFactory cacheFactory) {
        return new NamedObjectInstantiator(cacheFactory);
    }

    TaskDependencyFactory createTaskDependencyFactory() {
        return DefaultTaskDependencyFactory.withNoAssociatedProject();
    }

    DefaultFilePropertyFactory createFilePropertyFactory(PropertyHost propertyHost, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
        return new DefaultFilePropertyFactory(propertyHost, fileResolver, fileCollectionFactory);
    }

    StreamHasher createStreamHasher() {
        return new DefaultStreamHasher();
    }

    Deleter createDeleter(Clock clock, FileSystem fileSystem, OperatingSystem os) {
        return new DefaultDeleter(clock::getCurrentTime, fileSystem::isSymlink, os.isWindows());
    }

    PropertyFactory createPropertyFactory(PropertyHost propertyHost) {
        return new DefaultPropertyFactory(propertyHost);
    }

    ManagedFactoryRegistry createManagedFactoryRegistry(NamedObjectInstantiator namedObjectInstantiator, InstantiatorFactory instantiatorFactory, PropertyFactory propertyFactory, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory, FilePropertyFactory filePropertyFactory) {
        return new DefaultManagedFactoryRegistry().withFactories(
            instantiatorFactory.getManagedFactory(),
            new ConfigurableFileCollectionManagedFactory(fileCollectionFactory),
            new RegularFileManagedFactory(fileFactory),
            new RegularFilePropertyManagedFactory(filePropertyFactory),
            new DirectoryManagedFactory(fileFactory),
            new DirectoryPropertyManagedFactory(filePropertyFactory),
            new SetPropertyManagedFactory(propertyFactory),
            new ListPropertyManagedFactory(propertyFactory),
            new MapPropertyManagedFactory(propertyFactory),
            new PropertyManagedFactory(propertyFactory),
            new ProviderManagedFactory(),
            namedObjectInstantiator
        );
    }

    DefaultModuleRegistry createModuleRegistry(CurrentGradleInstallation currentGradleInstallation) {
        return new DefaultModuleRegistry(additionalModuleClassPath, currentGradleInstallation.getInstallation());
    }

    CurrentGradleInstallation createCurrentGradleInstallation() {
        return CurrentGradleInstallation.locate();
    }

    ClassLoaderFactory createClassLoaderFactory() {
        return new DefaultClassLoaderFactory();
    }

    BuildOperationRunner createBuildOperationRunner(
        Clock clock,
        CurrentBuildOperationRef currentBuildOperationRef,
        ProgressLoggerFactory progressLoggerFactory,
        BuildOperationIdFactory buildOperationIdFactory,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        BuildOperationListener listener = buildOperationListenerManager.getBroadcaster();
        return new DefaultBuildOperationRunner(
            currentBuildOperationRef,
            clock::getCurrentTime,
            buildOperationIdFactory,
            () -> new ProgressListenerAdapter(listener, progressLoggerFactory, clock)
        );
    }

    private static class ProgressListenerAdapter implements DefaultBuildOperationRunner.BuildOperationExecutionListener {
        private final BuildOperationListener buildOperationListener;
        private final ProgressLoggerFactory progressLoggerFactory;
        private final Clock clock;
        private ProgressLogger progressLogger;
        private ProgressLogger statusProgressLogger;

        public ProgressListenerAdapter(BuildOperationListener buildOperationListener, ProgressLoggerFactory progressLoggerFactory, Clock clock) {
            this.buildOperationListener = buildOperationListener;
            this.progressLoggerFactory = progressLoggerFactory;
            this.clock = clock;
        }

        @Override
        public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            buildOperationListener.started(descriptor, new OperationStartEvent(operationState.getStartTime()));
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, descriptor);
            this.progressLogger = progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
        }

        @Override
        public void progress(BuildOperationDescriptor descriptor, String status) {
            // Currently, need to start a new progress operation to hold the status, as changing the status of the progress operation replaces the
            // progress display name on the console, whereas we want to display both the progress display name and the status
            // This should be pushed down into the progress logger infrastructure so that an operation can have both a display name (that doesn't change) and
            // a status (that does)
            if (statusProgressLogger == null) {
                statusProgressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, progressLogger);
                statusProgressLogger.start(descriptor.getDisplayName(), status);
            } else {
                statusProgressLogger.progress(status);
            }
        }

        @Override
        public void progress(BuildOperationDescriptor descriptor, long progress, long total, String units, String status) {
            progress(descriptor, status);
            buildOperationListener.progress(descriptor.getId(), new OperationProgressEvent(clock.getCurrentTime(), new OperationProgressDetails(progress, total, units)));
        }

        @Override
        public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationRunner.ReadableBuildOperationContext context) {
            if (statusProgressLogger != null) {
                statusProgressLogger.completed();
            }
            progressLogger.completed(context.getStatus(), context.getFailure() != null);
            buildOperationListener.finished(descriptor, new OperationFinishEvent(operationState.getStartTime(), clock.getCurrentTime(), context.getFailure(), context.getResult()));
        }

        @Override
        public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
        }
    }
}
