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

import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.ResolvedDaemonJvm;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;

import java.io.InputStream;

/**
 * Takes care of instantiating and wiring together the services required by the daemon client.
 */
public class DaemonClientServices extends DaemonClientServicesSupport {
    public DaemonClientServices(ServiceRegistry parent, DaemonParameters daemonParameters, ResolvedDaemonJvm resolvedDaemonJvm, InputStream buildStandardInput) {
        super(parent, buildStandardInput);
        add(daemonParameters);
        add(resolvedDaemonJvm);
        addProvider(new DaemonRegistryServices(daemonParameters.getBaseDir()));
    }

    DaemonStarter createDaemonStarter(DaemonDir daemonDir, DaemonParameters daemonParameters, ResolvedDaemonJvm resolvedDaemonJvm, DaemonGreeter daemonGreeter, JvmVersionValidator jvmVersionValidator) {
        return new DefaultDaemonStarter(daemonDir, daemonParameters, resolvedDaemonJvm, daemonGreeter, jvmVersionValidator);
    }

    DaemonContext createDaemonContext(ProcessEnvironment processEnvironment, DaemonDir daemonDir, DaemonParameters daemonParameters, ResolvedDaemonJvm resolvedDaemonJvm) {
        DaemonContextBuilder builder = new DaemonContextBuilder(processEnvironment);
        builder.setDaemonRegistryDir(daemonDir.getBaseDir());
        builder.useDaemonParameters(daemonParameters, resolvedDaemonJvm);
        return builder.create();
    }
}
