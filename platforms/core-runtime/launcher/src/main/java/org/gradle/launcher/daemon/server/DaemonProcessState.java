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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.buildprocess.BuildProcessState;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.registry.DaemonRegistryServices;
import org.gradle.launcher.daemon.startup.DaemonServerConfiguration;

import java.io.Closeable;
import java.util.Arrays;

/**
 * Encapsulates the state of the daemon process.
 */
public class DaemonProcessState implements Closeable {
    private final BuildProcessState buildProcessState;

    public DaemonProcessState(DaemonServerConfiguration configuration, ServiceRegistry loggingServices, LoggingManagerInternal loggingManager) {
        // Merge the daemon services into the build process services
        // It would be better to separate these into different scopes, but many things still assume that daemon services are available in the global scope,
        // so keep them merged as a migration step
        buildProcessState = new BuildProcessState(
            !configuration.isSingleUse(),
            AgentStatus.of(configuration.isInstrumentationAgentAllowed()),
            CurrentGradleInstallation.locate(),
            ImmutableList.of(
                new DaemonServices(configuration, loggingManager),
                new DaemonRegistryServices(configuration.getBaseDir())
            ),
            Arrays.asList(
                loggingServices,
                NativeServices.getInstance()
            )
        );
    }

    public ServiceRegistry getServices() {
        return buildProcessState.getServices();
    }

    @Override
    public void close() {
        buildProcessState.close();
    }
}
