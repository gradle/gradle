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

import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonRequestContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;

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
        ServiceRegistry loggingServices = createLoggingServices(clientLoggingServices);
        return new DaemonClientServices(loggingServices, daemonParameters, requestContext, stdin);
    }

    /**
     * Creates the services for a {@link DaemonClient} that can be used to run a build in a single-use daemon.
     */
    public ServiceRegistry createSingleUseDaemonClientServices(ServiceLookup clientLoggingServices, DaemonParameters daemonParameters, DaemonRequestContext requestContext, InputStream stdin) {
        ServiceRegistry loggingServices = createLoggingServices(clientLoggingServices);
        return new SingleUseDaemonClientServices(loggingServices, daemonParameters, requestContext, stdin);
    }

    private DefaultServiceRegistry createLoggingServices(ServiceLookup clientLoggingServices) {
        // Need to use some specific logging services from the client-specific registry, rather than the global registry
        DefaultServiceRegistry loggingServices = new DefaultServiceRegistry(sharedServices);
        loggingServices.add(OutputEventListener.class, clientLoggingServices.get(OutputEventListener.class));
        loggingServices.add(GlobalUserInputReceiver.class, clientLoggingServices.get(GlobalUserInputReceiver.class));
        return loggingServices;
    }

    /**
     * Creates the services for sending simple messages to daemons.
     *
     * Currently, there are two clients which can be used from this registry:
     * - {@link DaemonStopClient} that can be used to stop daemons.
     * - {@link NotifyDaemonAboutChangedPathsClient} that can be used to notify daemons about changed paths.
     */
    public ServiceRegistry createMessageDaemonServices(ServiceLookup clientLoggingServices, DaemonParameters daemonParameters) {
        // These can always run inside the current JVM since we should not be forking a daemon to run them
        return createBuildClientServices(clientLoggingServices, daemonParameters, new DaemonRequestContext(Jvm.current(), null, Collections.emptyList(), false, NativeServices.NativeServicesMode.NOT_SET, DaemonParameters.Priority.NORMAL), new ByteArrayInputStream(new byte[0]));
    }
}
