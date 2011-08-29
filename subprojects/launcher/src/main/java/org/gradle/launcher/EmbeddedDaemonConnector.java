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
package org.gradle.launcher;

import org.gradle.StartParameter;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.util.GUtil;
import org.gradle.util.Jvm;
import org.gradle.util.DefaultClassLoaderFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A daemon connector that starts daemons by launching new daemons in the same jvm.
 */
public class EmbeddedDaemonConnector extends AbstractDaemonConnector {

    private static final Logger LOGGER = Logging.getLogger(EmbeddedDaemonConnector.class);
    
    private final File userHomeDir;
    
    public EmbeddedDaemonConnector(File userHomeDir) {
        this(userHomeDir, new EmbeddedDaemonRegistry());
    }
    
    public EmbeddedDaemonConnector(File userHomeDir, EmbeddedDaemonRegistry daemonRegistry) {
        super(daemonRegistry);
        this.userHomeDir = userHomeDir;
    }

    protected void startDaemon() {
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newCommandLineProcessLogging();
        DaemonServer server = new DaemonServer(getDaemonRegistry());
        StartParameter startParameter = new StartParameter();
        startParameter.setGradleUserHomeDir(userHomeDir);
        
        DaemonMain daemon = new DaemonMain(loggingServices, server, startParameter);
        
        // TODO - might be worth putting all daemon threads for the registry under one thread group
        Thread daemonThread = new Thread(daemon, "Gradle Daemon");
        daemonThread.setDaemon(true); // we don't want this thread to hold our process open
        daemonThread.start();
    }
    
    public EmbeddedDaemonRegistry getDaemonRegistry() {
        return (EmbeddedDaemonRegistry)super.getDaemonRegistry();
    }

}
