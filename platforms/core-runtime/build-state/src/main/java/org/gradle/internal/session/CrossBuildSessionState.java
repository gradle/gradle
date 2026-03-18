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

package org.gradle.internal.session;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.options.InternalOptionsFactory;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationsParameters;
import org.gradle.internal.operations.DefaultBuildOperationsParameters;
import org.gradle.internal.operations.trace.BuildOperationTrace;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.CrossBuildSessionParameters;
import org.gradle.internal.service.scopes.GradleModuleServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;
import java.io.File;
import java.util.List;

/**
 * Services to be shared across build sessions.
 * <p>
 * Generally, one regular Gradle invocation is conceptually a session.
 * However, the GradleBuild task is currently implemented in such a way that it uses a discrete session.
 * Having the GradleBuild task reuse the outer session is complicated because it <a href="https://github.com/gradle/gradle/issues/4559">may use a different Gradle user home</a>.
 * <p>
 * This set of services is added as a parent of each build session scope.
 */
@ServiceScope(Scope.CrossBuildSession.class)
public class CrossBuildSessionState implements Closeable {
    private final ServiceRegistry services;

    public CrossBuildSessionState(ServiceRegistry parent, StartParameterInternal startParameter, File userActionRootDir) {
        this.services = ServiceRegistryBuilder.builder()
            .scopeStrictly(Scope.CrossBuildSession.class)
            .displayName("cross session services")
            .parent(parent)
            .provider(new Services(startParameter, userActionRootDir))
            .build();
        // Trigger listener to wire itself in
        services.get(BuildOperationTrace.class);
    }

    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(services).stop();
    }

    private class Services implements ServiceRegistrationProvider {

        private final StartParameterInternal startParameter;
        private final File userActionRootDir;

        public Services(StartParameterInternal startParameter, File userActionRootDir) {
            this.startParameter = startParameter;
            this.userActionRootDir = userActionRootDir;
        }

        @Provides
        void configure(ServiceRegistration registration, List<GradleModuleServices> servicesProviders) {
            for (GradleModuleServices services : servicesProviders) {
                services.registerCrossBuildSessionServices(registration);
            }
            registration.add(CrossBuildSessionParameters.class, new CrossBuildSessionParameters(startParameter, userActionRootDir));
            registration.add(CrossBuildSessionState.class, CrossBuildSessionState.this);
            registration.add(BuildOperationsParameters.class, DefaultBuildOperationsParameters.class);
        }

        @Provides
        InternalOptions createInternalOptions(CrossBuildSessionParameters parameters) {
            return InternalOptionsFactory.createInternalOptions(startParameter, parameters.getUserActionRootDirectory());
        }
    }
}
