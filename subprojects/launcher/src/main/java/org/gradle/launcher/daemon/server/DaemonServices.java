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

import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.os.NativeServices;
import org.gradle.os.ProcessEnvironment;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.lang.management.ManagementFactory;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final File daemonBaseDir;
    private final Integer idleTimeoutMs;
    private final ServiceRegistry loggingServices;

    public DaemonServices(File daemonBaseDir, Integer idleTimeoutMs, ServiceRegistry loggingServices) {
        this.daemonBaseDir = daemonBaseDir;
        this.idleTimeoutMs = idleTimeoutMs;
        this.loggingServices = loggingServices;

        add(new DaemonRegistryServices(daemonBaseDir));
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected ProcessEnvironment createProcessEnvironment() {
        return get(NativeServices.class).get(ProcessEnvironment.class);
    }
    
    protected NativeServices createNativeServices() {
        return new NativeServices();
    }
    
    protected DaemonContext createDaemonContext() {
        DaemonContextBuilder builder = new DaemonContextBuilder(get(ProcessEnvironment.class));
        builder.setDaemonRegistryDir(daemonBaseDir);
        builder.setIdleTimeout(idleTimeoutMs);

        JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
        jvmOptions.setAllJvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments());
        builder.setDaemonOpts(jvmOptions.getAllJvmArgsWithoutSystemProperties());

        return builder.create();
    }

    protected Daemon createDaemon() {
        return new Daemon(
                new DaemonTcpServerConnector(),
                get(DaemonRegistry.class),
                get(DaemonContext.class),
                "password",
                new DefaultDaemonCommandExecuter(
                        loggingServices,
                        get(ExecutorFactory.class),
                        get(ProcessEnvironment.class)),
                get(ExecutorFactory.class));
    }

}
