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

package org.gradle.tooling.internal.provider;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.loadercache.ModelClassLoaderFactory;
import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.daemon.client.serialization.ClasspathInferer;
import org.gradle.internal.daemon.client.serialization.ClientSidePayloadClassLoaderFactory;
import org.gradle.internal.daemon.client.serialization.ClientSidePayloadClassLoaderRegistry;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory;
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices;
import org.gradle.launcher.daemon.client.DaemonStopClientExecuter;
import org.gradle.launcher.daemon.client.NotifyDaemonClientExecuter;
import org.gradle.launcher.exec.BuildExecutor;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Shared services for a tooling API provider connection.
 */
public class ConnectionScopeServices implements ServiceRegistrationProvider {
    void configure(ServiceRegistration serviceRegistration) {
        serviceRegistration.addProvider(new DaemonClientGlobalServices());
    }

    @Provides
    ShutdownCoordinator createShutdownCoordinator(ListenerManager listenerManager, DaemonStopClientExecuter daemonStopClient) {
        ShutdownCoordinator shutdownCoordinator = new ShutdownCoordinator(daemonStopClient);
        listenerManager.addListener(shutdownCoordinator);
        return shutdownCoordinator;
    }

    @Provides
    DaemonStopClientExecuter createDaemonStopClientFactory(DaemonClientFactory daemonClientFactory, FileCollectionFactory fileCollectionFactory) {
        return new DaemonStopClientExecuter(daemonClientFactory);
    }

    @Provides
    NotifyDaemonClientExecuter createNotifyDaemonClientExecuter(DaemonClientFactory daemonClientFactory, FileCollectionFactory fileCollectionFactory) {
        return new NotifyDaemonClientExecuter(daemonClientFactory);
    }

    @Provides
    ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher() {
        return new ClassLoaderHierarchyHasher() {
            @Nullable
            @Override
            public HashCode getClassLoaderHash(ClassLoader classLoader) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Provides
    IsolatableSerializerRegistry createIsolatableSerializerRegistry(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        return new IsolatableSerializerRegistry(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    @Provides
    IsolatableFactory createIsolatableFactory(
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ManagedFactoryRegistry managedFactoryRegistry
    ) {
        return new DefaultIsolatableFactory(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    @Provides
    ProviderConnection createProviderConnection(
        BuildExecutor buildActionExecuter,
        DaemonClientFactory daemonClientFactory,
        BuildLayoutFactory buildLayoutFactory,
        ServiceRegistry serviceRegistry,
        FileCollectionFactory fileCollectionFactory,
        GlobalUserInputReceiver userInput,
        UserInputReader userInputReader,
        ShutdownCoordinator shutdownCoordinator,
        NotifyDaemonClientExecuter notifyDaemonClientExecuter,
        IsolatableSerializerRegistry isolatableSerializerRegistry
    ) {
        ClassLoaderCache classLoaderCache = new ClassLoaderCache();

        ClassLoader parent = this.getClass().getClassLoader();
        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        filterSpec.allowPackage("org.gradle.tooling.internal.protocol");
        filterSpec.allowClass(TaskExecutionRequest.class);
        FilteringClassLoader modelClassLoader = new FilteringClassLoader(parent, filterSpec);

        PayloadSerializer payloadSerializer = new PayloadSerializer(
            new WellKnownClassLoaderRegistry(
                new ClientSidePayloadClassLoaderRegistry(
                    new DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        new ClientSidePayloadClassLoaderFactory(
                            new ModelClassLoaderFactory(modelClassLoader)
                        )
                    ),
                    new ClasspathInferer(),
                    classLoaderCache
                )
            )
        );

        return new ProviderConnection(
            serviceRegistry,
            buildLayoutFactory,
            daemonClientFactory,
            buildActionExecuter,
            payloadSerializer,
            fileCollectionFactory,
            userInput,
            userInputReader,
            shutdownCoordinator,
            notifyDaemonClientExecuter,
            isolatableSerializerRegistry
        );
    }

    @Provides
    ProtocolToModelAdapter createProtocolToModelAdapter() {
        return new ProtocolToModelAdapter();
    }
}
