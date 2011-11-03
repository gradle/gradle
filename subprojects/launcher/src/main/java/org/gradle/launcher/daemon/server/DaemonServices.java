/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server;

import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;

import java.io.File;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final ServiceRegistry loggingServices;

    public DaemonServices(File userHomeDir, ServiceRegistry loggingServices) {
        this.loggingServices = loggingServices;
        add(new DaemonRegistryServices(userHomeDir));
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected DaemonContext createDaemonContext() {
        return new DaemonContextBuilder().create();
    }

    protected Daemon createDaemon() {
        return new Daemon(
                new DaemonTcpServerConnector(),
                get(DaemonRegistry.class),
                get(DaemonContext.class),
                new DefaultDaemonCommandExecuter(
                        loggingServices,
                        get(ExecutorFactory.class)),
                get(ExecutorFactory.class));
    }
}
