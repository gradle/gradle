/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.observability;

import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

/**
 * Registers observability services (e.g. JFR event emission) via the {@link org.gradle.internal.service.scopes.GradleModuleServices} SPI.
 *
 * <p>Discovered automatically by the service-loader mechanism — no changes to {@code CrossBuildSessionState} required.
 */
public class TracingServices extends AbstractGradleModuleServices {

    @Override
    public void registerCrossBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new CrossBuildSessionServices());
    }

    private static class CrossBuildSessionServices implements ServiceRegistrationProvider {
        // Use a configure() method so the listener is eagerly created and registered during
        // container build — @Provides factory methods are lazy and would never be invoked
        // unless something explicitly requests BuildOperationJfrListener.
        @SuppressWarnings("UnusedMethod") // called reflectively by the service registry
        void configure(ServiceRegistration registration, BuildOperationListenerManager buildOperationListenerManager) {
            registration.add(BuildOperationJfrListener.class, new BuildOperationJfrListener(buildOperationListenerManager));
        }
    }
}
