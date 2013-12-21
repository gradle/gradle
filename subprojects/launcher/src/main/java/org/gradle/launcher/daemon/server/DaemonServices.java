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

import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.server.exec.DaemonHygieneAction;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.logging.LoggingManagerInternal;

import java.io.File;
import java.util.UUID;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final DaemonServerConfiguration configuration;
    private final LoggingManagerInternal loggingManager;
    private final static Logger LOGGER = Logging.getLogger(DaemonServices.class);

    public DaemonServices(DaemonServerConfiguration configuration, ServiceRegistry loggingServices, LoggingManagerInternal loggingManager) {
        super(NativeServices.getInstance(), loggingServices);
        this.configuration = configuration;
        this.loggingManager = loggingManager;

        addProvider(new DaemonRegistryServices(configuration.getBaseDir()));
        addProvider(new GlobalScopeServices(true));
    }

    protected DaemonContext createDaemonContext() {
        DaemonContextBuilder builder = new DaemonContextBuilder(get(ProcessEnvironment.class));
        builder.setDaemonRegistryDir(configuration.getBaseDir());
        builder.setIdleTimeout(configuration.getIdleTimeout());
        builder.setUid(configuration.getUid());

        LOGGER.debug("Creating daemon context with opts: {}", configuration.getJvmOptions());
        
        builder.setDaemonOpts(configuration.getJvmOptions());

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
                        get(GradleLauncherFactory.class),
                        get(ProcessEnvironment.class),
                        loggingManager,
                        getDaemonLogFile(), new DaemonHygieneAction()),
                get(ExecutorFactory.class));
    }

}
