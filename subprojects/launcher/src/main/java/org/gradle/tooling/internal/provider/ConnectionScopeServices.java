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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.ClasspathInferer;
import org.gradle.tooling.internal.provider.serialization.ClientSidePayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.ClientSidePayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.ModelClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;

/**
 * Shared services for a tooling API provider connection.
 */
public class ConnectionScopeServices {
    void configure(ServiceRegistration serviceRegistration) {
        serviceRegistration.addProvider(new GlobalScopeServices(true));
        serviceRegistration.addProvider(new DaemonClientGlobalServices());
    }

    ShutdownCoordinator createShutdownCoordinator(ListenerManager listenerManager, DaemonClientFactory daemonClientFactory, OutputEventListener outputEventListener, FileCollectionFactory fileCollectionFactory) {
        ServiceRegistry clientServices = daemonClientFactory.createStopDaemonServices(outputEventListener, new DaemonParameters(new BuildLayoutParameters(), fileCollectionFactory));
        DaemonStopClient client = clientServices.get(DaemonStopClient.class);
        ShutdownCoordinator shutdownCoordinator = new ShutdownCoordinator(client);
        listenerManager.addListener(shutdownCoordinator);
        return shutdownCoordinator;
    }

    ProviderConnection createProviderConnection(BuildExecuter buildActionExecuter,
                                                DaemonClientFactory daemonClientFactory,
                                                BuildLayoutFactory buildLayoutFactory,
                                                ServiceRegistry serviceRegistry,
                                                JvmVersionDetector jvmVersionDetector,
                                                FileCollectionFactory fileCollectionFactory,
                                                // This is here to trigger creation of the ShutdownCoordinator. Could do this in a nicer way
                                                ShutdownCoordinator shutdownCoordinator) {
        ClassLoaderCache classLoaderCache = new ClassLoaderCache();
        return new ProviderConnection(
                serviceRegistry,
                buildLayoutFactory,
                daemonClientFactory,
                buildActionExecuter,
                new PayloadSerializer(
                        new WellKnownClassLoaderRegistry(
                            new ClientSidePayloadClassLoaderRegistry(
                                new DefaultPayloadClassLoaderRegistry(
                                    classLoaderCache,
                                    new ClientSidePayloadClassLoaderFactory(
                                        new ModelClassLoaderFactory())),
                                new ClasspathInferer(),
                                classLoaderCache))),
            jvmVersionDetector,
            fileCollectionFactory
        );
    }

    ProtocolToModelAdapter createProtocolToModelAdapter() {
        return new ProtocolToModelAdapter();
    }
}
