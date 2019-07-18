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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.initialization.DefaultLegacyTypesSupport;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;

import static org.gradle.api.internal.provider.ManagedFactories.*;
import static org.gradle.api.internal.file.ManagedFactories.*;
import static org.gradle.api.internal.file.collections.ManagedFactories.*;

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
    
    ManagedFactoryRegistry createManagedFactoryRegistry(NamedObjectInstantiator namedObjectInstantiator, FileResolver fileResolver, InstantiatorFactory instantiatorFactory) {
        return new DefaultManagedFactoryRegistry().withFactories(
                instantiatorFactory.getManagedFactory(),
                new ConfigurableFileCollectionManagedFactory(fileResolver),
                new RegularFileManagedFactory(),
                new RegularFilePropertyManagedFactory(fileResolver),
                new DirectoryManagedFactory(fileResolver),
                new DirectoryPropertyManagedFactory(fileResolver),
                new SetPropertyManagedFactory(),
                new ListPropertyManagedFactory(),
                new MapPropertyManagedFactory(),
                new PropertyManagedFactory(),
                new ProviderManagedFactory(),
                namedObjectInstantiator
        );
    }
}
