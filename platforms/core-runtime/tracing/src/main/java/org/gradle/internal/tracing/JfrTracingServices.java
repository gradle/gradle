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

package org.gradle.internal.tracing;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

/**
 * Registers the Gradle-managed JFR recording manager as a build-session service.
 */
public class JfrTracingServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionServices());
    }

    private static class BuildSessionServices implements ServiceRegistrationProvider {
        @SuppressWarnings("UnusedMethod")
        void configure(ServiceRegistration registration, InternalOptions internalOptions, StartParameterInternal startParameter, BuildLayoutFactory buildLayoutFactory) {
            registration.add(JfrRecordingManager.class, new JfrRecordingManager(internalOptions, startParameter, buildLayoutFactory));
        }
    }
}
