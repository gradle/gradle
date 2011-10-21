/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.Factory;
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServerConnector;
import org.gradle.launcher.daemon.server.DaemonTcpServerConnector;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;

/**
 * A daemon connector that starts daemons by launching new daemons in the same jvm.
 */
public class EmbeddedDaemonConnector extends AbstractDaemonConnector<EmbeddedDaemonRegistry> {

    private final Factory<LoggingServiceRegistry> loggingServicesFactory;

    public EmbeddedDaemonConnector() {
        this(new EmbeddedDaemonRegistry());
    }

    public EmbeddedDaemonConnector(EmbeddedDaemonRegistry daemonRegistry) {
        this(daemonRegistry, new Factory<LoggingServiceRegistry>() { 
            public LoggingServiceRegistry create() { return LoggingServiceRegistry.newCommandLineProcessLogging(); }
        });
    }

    public EmbeddedDaemonConnector(EmbeddedDaemonRegistry daemonRegistry, Factory<LoggingServiceRegistry> loggingServicesFactory) {
        super(daemonRegistry);
        this.loggingServicesFactory = loggingServicesFactory;
    }


    protected void startDaemon() {
        EmbeddedDaemonRegistry daemonRegistry = getDaemonRegistry();

        LoggingServiceRegistry loggingServices = loggingServicesFactory.create();
        DaemonServerConnector server = new DaemonTcpServerConnector();
        DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
        daemonRegistry.startDaemon(new Daemon(server, daemonRegistry, new DefaultDaemonCommandExecuter(loggingServices, executorFactory), executorFactory));
    }

}
