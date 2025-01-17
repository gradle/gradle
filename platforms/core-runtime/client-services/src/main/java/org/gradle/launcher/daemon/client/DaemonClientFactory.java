/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.client;

import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.toolchain.DaemonClientToolchainServices;

import java.io.File;
import java.io.InputStream;

@ServiceScope(Scope.Global.class)
public class DaemonClientFactory {
    private final ServiceRegistry sharedServices;

    public DaemonClientFactory(ServiceRegistry sharedServices) {
        this.sharedServices = sharedServices;
    }

    /**
     * Creates the services for a {@link DaemonClient} that can be used to run builds.
     */
    public ServiceRegistry createBuildClientServices(ServiceLookup clientLoggingServices, DaemonParameters daemonParameters, DaemonRequestContext requestContext, InputStream stdin) {
        return clientServicesBuilder(clientLoggingServices, daemonParameters, requestContext)
            .provider(new DaemonClientServices(stdin))
            .build();
    }

    /**
     * Creates the services for a {@link DaemonClient} that can be used to run a build in a single-use daemon.
     */
    public ServiceRegistry createSingleUseDaemonClientServices(ServiceLookup clientLoggingServices, DaemonParameters daemonParameters, DaemonRequestContext requestContext, InputStream stdin) {
        return clientServicesBuilder(clientLoggingServices, daemonParameters, requestContext)
            .provider(new SingleUseDaemonClientServices(stdin))
            .build();
    }

    private ServiceRegistryBuilder clientServicesBuilder(ServiceLookup clientLoggingServices, DaemonParameters daemonParameters, DaemonRequestContext requestContext) {
        ServiceRegistry loggingServices = createLoggingServices(clientLoggingServices);

        return ServiceRegistryBuilder.builder()
            .displayName("daemon client services")
            .parent(loggingServices)
            .provider(new ServiceRegistrationProvider() {
                @Provides
                DaemonParameters createDaemonParameters() {
                    return daemonParameters;
                }

                @Provides
                DaemonRequestContext createDaemonRequestContext() {
                    return requestContext;
                }
            })
            .provider(new DaemonRegistryServices(daemonParameters.getBaseDir()))
            .provider(new DaemonClientToolchainServices(daemonParameters.getToolchainConfiguration()));
    }

    private ServiceRegistry createLoggingServices(ServiceLookup clientLoggingServices) {
        // Need to use some specific logging services from the client-specific registry, rather than the global registry
        OutputEventListener clientOutputEventListener = (OutputEventListener) clientLoggingServices.get(OutputEventListener.class);
        GlobalUserInputReceiver clientGlobalUserInputReceiver = (GlobalUserInputReceiver) clientLoggingServices.get(GlobalUserInputReceiver.class);

        return ServiceRegistryBuilder.builder()
            .displayName("logging services")
            .parent(sharedServices)
            .provider(new ServiceRegistrationProvider() {
                @SuppressWarnings("unused")
                void configure(ServiceRegistration registration) {
                    registration.add(OutputEventListener.class, clientOutputEventListener);
                    registration.add(GlobalUserInputReceiver.class, clientGlobalUserInputReceiver);
                }
            })
            .build();
    }

    /**
     * Creates the services for sending simple messages to daemons.
     *
     * Currently, there are two clients which can be used from this registry:
     * - {@link DaemonStopClient} that can be used to stop daemons.
     * - {@link NotifyDaemonAboutChangedPathsClient} that can be used to notify daemons about changed paths.
     */
    public ServiceRegistry createMessageDaemonServices(ServiceLookup clientLoggingServices, File daemonBaseDir) {
        // These can always run inside the current JVM since we should not be forking a daemon to run them
        ServiceRegistry loggingServices = createLoggingServices(clientLoggingServices);

        return ServiceRegistryBuilder.builder()
            .displayName("daemon client services")
            .parent(loggingServices)
            .provider(new DaemonRegistryServices(daemonBaseDir))
            .provider(new DaemonClientMessageServices())
            .build();
    }
}
