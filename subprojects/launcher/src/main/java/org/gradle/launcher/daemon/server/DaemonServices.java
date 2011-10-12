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
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.DefaultProcessMetaDataProvider;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.os.ProcessEnvironment;
import org.gradle.os.jna.NativeEnvironment;

import java.io.File;

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
public class DaemonServices extends DefaultServiceRegistry {
    private final File userHomeDir;
    private final ServiceRegistry loggingServices;

    public DaemonServices(File userHomeDir, ServiceRegistry loggingServices) {
        this.userHomeDir = userHomeDir;
        this.loggingServices = loggingServices;
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected ProcessEnvironment createProcessEnvironment() {
        return NativeEnvironment.current();
    }
    
    protected DaemonDir createDaemonDir() {
        return new DaemonDir(
                userHomeDir,
                get(ProcessEnvironment.class));
    }
    
    protected FileLockManager createFileLockManager() {
        return new DefaultFileLockManager(
                new DefaultProcessMetaDataProvider(
                        get(ProcessEnvironment.class)));
    }

    protected DaemonRegistry createDaemonRegistry() {
        return new PersistentDaemonRegistry(
                get(DaemonDir.class).getRegistry(),
                get(FileLockManager.class));
    }
    
    protected Daemon createDaemon() {
        return new Daemon(
                new DaemonTcpServerConnector(),
                get(DaemonRegistry.class),
                new DefaultDaemonCommandExecuter(
                        loggingServices,
                        get(ExecutorFactory.class)),
                get(ExecutorFactory.class));
    }
}
