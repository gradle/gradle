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
package org.gradle.launcher.daemon;

import org.gradle.logging.LoggingServiceRegistry;

/**
 * A daemon connector that starts daemons by launching new daemons in the same jvm.
 */
public class EmbeddedDaemonConnector extends AbstractDaemonConnector<EmbeddedDaemonRegistry> {

    public EmbeddedDaemonConnector() {
        this(new EmbeddedDaemonRegistry());
    }
    
    public EmbeddedDaemonConnector(EmbeddedDaemonRegistry daemonRegistry) {
        super(daemonRegistry);
    }

    protected void startDaemon() {
        EmbeddedDaemonRegistry daemonRegistry = getDaemonRegistry();

        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newCommandLineProcessLogging();
        DaemonServerConnector server = new DaemonTcpServerConnector();

        daemonRegistry.startDaemon(new Daemon(loggingServices, server, daemonRegistry));
    }

}
