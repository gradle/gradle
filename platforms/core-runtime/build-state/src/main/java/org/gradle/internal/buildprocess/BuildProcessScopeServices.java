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

import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.buildevents.BuildLoggerFactory;
import org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor;
import org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor;
import org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor;
import org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GradleModuleServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.launcher.exec.BuildExecutor;

import java.util.List;

public class BuildProcessScopeServices implements ServiceRegistrationProvider {
    void configure(ServiceRegistration registration, ClassLoaderRegistry classLoaderRegistry) {
        List<GradleModuleServices> servicesProviders = new DefaultServiceLocator(classLoaderRegistry.getRuntimeClassLoader(), classLoaderRegistry.getPluginsClassLoader()).getAll(GradleModuleServices.class);
        for (GradleModuleServices services : servicesProviders) {
            registration.add(GradleModuleServices.class, services);
            services.registerGlobalServices(registration);
        }
    }

    @Provides
    BuildExecutor createBuildExecuter(
        LoggingManagerInternal loggingManager,
        BuildLoggerFactory buildLoggerFactory,
        GradleUserHomeScopeServiceRegistry userHomeServiceRegistry,
        ServiceRegistry globalServices
    ) {
        return new SetupLoggingActionExecutor(
            loggingManager,
            new SessionFailureReportingActionExecutor(
                buildLoggerFactory,
                new StartParamsValidatingActionExecutor(
                    new BuildSessionLifecycleBuildActionExecutor(userHomeServiceRegistry, globalServices)
                )
            )
        );
    }
}
