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
package org.gradle.launcher.daemon.client;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.DefaultProcessMetaDataProvider;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.nativeplatform.*;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.bootstrap.DaemonGreeter;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;

import java.io.InputStream;

/**
 * Takes care of instantiating and wiring together the services required by the daemon client.
 */
public class DaemonClientServices extends DaemonClientServicesSupport {
    private final DaemonParameters daemonParameters;

    public DaemonClientServices(ServiceRegistry loggingServices, DaemonParameters daemonParameters, InputStream buildStandardInput) {
        super(loggingServices, buildStandardInput);
        this.daemonParameters = daemonParameters;
        addProvider(new DaemonRegistryServices(daemonParameters.getBaseDir()));
    }

    protected FileLockManager createFileLockManager(ProcessEnvironment processEnvironment) {
        return new DefaultFileLockManager(new DefaultProcessMetaDataProvider(processEnvironment), new NoOpFileLockContentionHandler());
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected DocumentationRegistry createDocumentationRegistry() {
        return new DocumentationRegistry();
    }

    public DaemonStarter createDaemonStarter() {
        return new DefaultDaemonStarter(get(DaemonDir.class), daemonParameters, get(DaemonGreeter.class));
    }

    protected DaemonGreeter createDaemonGreeter() {
        return new DaemonGreeter(get(DocumentationRegistry.class));
    }

    protected void configureDaemonContextBuilder(DaemonContextBuilder builder) {
        builder.setDaemonRegistryDir(get(DaemonDir.class).getBaseDir());
        builder.useDaemonParameters(daemonParameters);
    }

    public DaemonParameters getDaemonParameters() {
        return daemonParameters;
    }
}
