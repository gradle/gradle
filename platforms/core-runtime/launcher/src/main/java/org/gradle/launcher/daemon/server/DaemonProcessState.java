/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.buildprocess.BuildProcessState;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates the state of the daemon process.
 */
public class DaemonProcessState implements Closeable {
    private final BuildProcessState buildProcessState;
    private final AtomicReference<DaemonStopState> stopState = new AtomicReference<>();

    public DaemonProcessState(DaemonServerConfiguration configuration, ServiceRegistry loggingServices, LoggingManagerInternal loggingManager, ClassPath additionalModuleClassPath) {
        // Merge the daemon services into the build process services
        // It would be better to separate these into different scopes, but many things still assume that daemon services are available in the global scope,
        // so keep them merged as a migration step
        buildProcessState = new BuildProcessState(!configuration.isSingleUse(), AgentStatus.of(configuration.isInstrumentationAgentAllowed()), additionalModuleClassPath, loggingServices, NativeServices.getInstance()) {
            @Override
            protected void addProviders(ServiceRegistryBuilder builder) {
                builder.provider(new DaemonServices(configuration, loggingManager));
                builder.provider(new DaemonRegistryServices(configuration.getBaseDir()));
            }
        };
    }

    public ServiceRegistry getServices() {
        return buildProcessState.getServices();
    }

    public void stopped(DaemonStopState stopState) {
        this.stopState.set(stopState);
    }

    @Override
    public void close() {
        if (stopState.get() == DaemonStopState.Forced) {
            // The daemon could not be stopped cleanly, so the services could still be doing work.
            // Don't attempt to stop the services, just stop this process
            return;
        }

        // Daemon has finished work, so stop the services
        buildProcessState.close();
    }
}
