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

package org.gradle.internal.buildconfiguration.services;

import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.buildconfiguration.resolvers.DefaultToolchainRepositoriesResolver;
import org.gradle.internal.buildconfiguration.resolvers.ToolchainRepositoriesResolver;
import org.gradle.internal.buildconfiguration.tasks.UpdateDaemonJvmModifier;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;

public class BuildConfigurationServices extends AbstractPluginServiceRegistry {

    protected static class ProjectScopeServices {
        protected UpdateDaemonJvmModifier createUpdateDaemonJvmModifier(ToolchainRepositoriesResolver toolchainRepositoriesResolver) {
            return new UpdateDaemonJvmModifier(toolchainRepositoriesResolver);
        }

        protected ToolchainRepositoriesResolver createToolchainRepositoriesResolver(JavaToolchainResolverRegistry javaToolchainResolverRegistry, ObjectFactory objectFactory) {
            return new DefaultToolchainRepositoriesResolver(javaToolchainResolverRegistry, objectFactory);
        }
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeServices());
    }
}
