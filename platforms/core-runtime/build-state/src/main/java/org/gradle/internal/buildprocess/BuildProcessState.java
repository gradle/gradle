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

package org.gradle.internal.buildprocess;

import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.Scope;

import java.io.Closeable;
import java.io.IOException;

/**
 * Encapsulates the state of a build process, such as the Gradle daemon.
 */
public class BuildProcessState implements Closeable {
    private final ServiceRegistry services;

    public BuildProcessState(final boolean longLiving, AgentStatus agentStatus, ClassPath additionalModuleClassPath, ServiceRegistry nativeServices, ServiceRegistry loggingServices) {
        services = ServiceRegistryBuilder.builder()
            .scopeStrictly(Scope.Global.class)
            .displayName("Global services")
            .parent(loggingServices)
            .parent(nativeServices)
            .provider(new GlobalScopeServices(longLiving, agentStatus, additionalModuleClassPath))
            .build();
    }

    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    public void close() throws IOException {
        // Force the user home services to be stopped first, because the dependencies between the user home services and the global services are not preserved currently
        CompositeStoppable.stoppable(services.get(GradleUserHomeScopeServiceRegistry.class), services).stop();
    }
}
