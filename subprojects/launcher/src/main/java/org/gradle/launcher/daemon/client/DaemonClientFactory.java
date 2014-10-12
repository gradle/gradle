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

import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.logging.internal.OutputEventListener;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class DaemonClientFactory {
    private final ServiceRegistry sharedServices;

    public DaemonClientFactory(ServiceRegistry sharedServices) {
        this.sharedServices = sharedServices;
    }

    /**
     * Creates the services for a {@link DaemonClient} that can be used to run builds.
     */
    public ServiceRegistry createBuildClientServices(OutputEventListener loggingReceiver, DaemonParameters daemonParameters, InputStream stdin) {
        DefaultServiceRegistry loggingServices = new DefaultServiceRegistry(sharedServices);
        loggingServices.add(OutputEventListener.class, loggingReceiver);
        return new DaemonClientServices(loggingServices, daemonParameters, stdin);
    }

    /**
     * Creates the services for a {@link DaemonClient} that can be used to run a build in a single-use daemon.
     */
    public ServiceRegistry createSingleUseDaemonClientServices(OutputEventListener loggingReceiver, DaemonParameters daemonParameters, InputStream stdin) {
        DefaultServiceRegistry loggingServices = new DefaultServiceRegistry(sharedServices);
        loggingServices.add(OutputEventListener.class, loggingReceiver);
        return new SingleUseDaemonClientServices(loggingServices, daemonParameters, stdin);
    }

    /**
     * Creates the services for a {@link DaemonStopClient} that can be used to stop builds.
     */
    public ServiceRegistry createStopDaemonServices(OutputEventListener loggingReceiver, DaemonParameters daemonParameters) {
        DefaultServiceRegistry loggingServices = new DefaultServiceRegistry(sharedServices);
        loggingServices.add(OutputEventListener.class, loggingReceiver);
        return new DaemonClientServices(loggingServices, daemonParameters, new ByteArrayInputStream(new byte[0]));
    }
}
