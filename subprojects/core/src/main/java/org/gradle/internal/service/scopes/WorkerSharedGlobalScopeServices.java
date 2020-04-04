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

import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.impl.DefaultDeleter;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;

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

    TemporaryFileProvider createTemporaryFileProvider() {
        return new TmpDirTemporaryFileProvider();
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
}
