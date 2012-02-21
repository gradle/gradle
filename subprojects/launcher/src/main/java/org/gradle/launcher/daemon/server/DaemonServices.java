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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final File daemonBaseDir;
    private final Integer idleTimeoutMs;
    private final String daemonUid;
    private final List<String> daemonJvmOptions;
    private final ServiceRegistry loggingServices;
    private final LoggingManagerInternal loggingManager;
    private final static Logger LOGGER = Logging.getLogger(DaemonServices.class);

    public DaemonServices(File daemonBaseDir, Integer idleTimeoutMs, String daemonUid, ServiceRegistry loggingServices, 
                          LoggingManagerInternal loggingManager, List<String> daemonJvmOptions) {
        this.daemonBaseDir = daemonBaseDir;
        this.idleTimeoutMs = idleTimeoutMs;
        this.loggingServices = loggingServices;
        this.loggingManager = loggingManager;
        this.daemonUid = daemonUid;
        this.daemonJvmOptions = daemonJvmOptions;

        add(new NativeServices());
        add(new DaemonRegistryServices(daemonBaseDir));
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected DaemonContext createDaemonContext() {
        DaemonContextBuilder builder = new DaemonContextBuilder(get(ProcessEnvironment.class));
        builder.setDaemonRegistryDir(daemonBaseDir);
        builder.setIdleTimeout(idleTimeoutMs);
        builder.setUid(daemonUid);

        LOGGER.debug("Creating daemon context with opts: {}", daemonJvmOptions);
        
        builder.setDaemonOpts(daemonJvmOptions);

        return builder.create();
    }
    
    public File getDaemonLogFile() {
        final DaemonContext daemonContext = get(DaemonContext.class);
        final Long pid = daemonContext.getPid();
        String fileName = String.format("daemon-%s.out.log", pid == null ? UUID.randomUUID() : pid);
        return new File(get(DaemonDir.class).getVersionedDir(), fileName);
    }

    protected Daemon createDaemon() {
        return new Daemon(
                new DaemonTcpServerConnector(),
                get(DaemonRegistry.class),
                get(DaemonContext.class),
                "password",
                new DefaultDaemonCommandExecuter(
                        new DefaultGradleLauncherFactory(loggingServices),
                        get(ExecutorFactory.class),
                        get(ProcessEnvironment.class),
                        loggingManager,
                        getDaemonLogFile()),
                get(ExecutorFactory.class));
    }

}
