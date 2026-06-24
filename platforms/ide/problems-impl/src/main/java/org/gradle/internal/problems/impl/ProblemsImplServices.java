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

package org.gradle.internal.problems.impl;

import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager;
import org.gradle.internal.problems.BoundedCallerStackCapturer;
import org.gradle.internal.problems.ProblemLocationAnalyzer;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

public class ProblemsImplServices extends AbstractGradleModuleServices {
    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new BuildTreeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionServices());
    }

    private static class BuildTreeServices implements ServiceRegistrationProvider {
        @Provides
        BoundedCallerStackCapturer createBoundedCallerStackCapturer(RegisteredScripts registeredScripts) {
            return new DefaultBoundedCallerStackCapturer(registeredScripts);
        }
    }

    private static class BuildSessionServices implements ServiceRegistrationProvider {
        @Provides
        RegisteredScripts createRegisteredScripts(ClassLoaderScopeRegistryListenerManager listenerManager) {
            return new DefaultRegisteredScripts(listenerManager);
        }

        @Provides
        ProblemLocationAnalyzer createProblemLocationAnalyzer(RegisteredScripts registeredScripts) {
            return new DefaultProblemLocationAnalyzer(registeredScripts);
        }
    }
}
