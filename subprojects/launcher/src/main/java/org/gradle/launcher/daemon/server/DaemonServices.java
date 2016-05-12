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
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.launcher.daemon.server.health.DaemonHealthServices;
import org.gradle.launcher.daemon.server.health.DefaultDaemonHealthServices;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final DaemonServerConfiguration configuration;
    private final LoggingManagerInternal loggingManager;
    private final static Logger LOGGER = Logging.getLogger(DaemonServices.class);

    public DaemonServices(DaemonServerConfiguration configuration, ServiceRegistry loggingServices, LoggingManagerInternal loggingManager, ClassPath additionalModuleClassPath) {
        super(NativeServices.getInstance(), loggingServices);
        this.configuration = configuration;
        this.loggingManager = loggingManager;

        addProvider(new DaemonRegistryServices(configuration.getBaseDir()));
        addProvider(new GlobalScopeServices(true, additionalModuleClassPath));
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
        String fileName = "daemon-" + (pid == null ? UUID.randomUUID() : pid) + ".out.log";
        return new File(get(DaemonDir.class).getVersionedDir(), fileName);
    }

    protected DaemonHealthServices createDaemonHealthServices(ScheduledExecutorService scheduledExecutorService) {
        return new DefaultDaemonHealthServices(scheduledExecutorService);
    }

    protected ScheduledExecutorService createScheduledExecutorService() {
        return Executors.newScheduledThreadPool(1);
    }

    protected Daemon createDaemon(BuildExecuter buildActionExecuter) {
        return new Daemon(
            new DaemonTcpServerConnector(
                get(ExecutorFactory.class),
                get(MessagingServices.class).get(InetAddressFactory.class)
            ),
            get(DaemonRegistry.class),
            get(DaemonContext.class),
            new DefaultDaemonCommandExecuter(
                buildActionExecuter,
                this,
                get(ProcessEnvironment.class),
                loggingManager,
                getDaemonLogFile(),
                get(DaemonHealthServices.class)
            ),
            get(ExecutorFactory.class),
            get(ScheduledExecutorService.class)
        );
    }

}
