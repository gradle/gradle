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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;

import java.io.Closeable;
import java.io.IOException;

public class DaemonProcessState implements Closeable {
    private final BuildProcessState buildProcessState;
    private final DaemonServices services;

    public DaemonProcessState(DaemonServerConfiguration configuration, ServiceRegistry loggingServices, LoggingManagerInternal loggingManager, ClassPath additionalModuleClassPath) {
        buildProcessState = new BuildProcessState(!configuration.isSingleUse(), AgentStatus.of(configuration.isInstrumentationAgentAllowed()), additionalModuleClassPath, loggingServices, NativeServices.getInstance());
        services = new DaemonServices(configuration, buildProcessState.getServices(), loggingManager);
    }

    public DaemonServices getServices() {
        return services;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(services, buildProcessState).stop();
    }
}
