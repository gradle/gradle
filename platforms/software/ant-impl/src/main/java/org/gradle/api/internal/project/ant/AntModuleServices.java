/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.project.ant;

import org.gradle.api.Project;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.project.AntBuilderFactory;
import org.gradle.api.internal.project.DefaultAntBuilderFactory;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class AntModuleServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new AntBuildScopeServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new AntProjectScopeServices());
    }

    private static class AntBuildScopeServices implements ServiceRegistrationProvider {
        @Provides
        IsolatedAntBuilder createIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory, ModuleRegistry moduleRegistry) {
            return new DefaultIsolatedAntBuilder(classPathRegistry, classLoaderFactory, moduleRegistry);
        }
    }

    private static class AntProjectScopeServices implements ServiceRegistrationProvider {
        @Provides
        AntBuilderFactory createAntBuilderFactory(Project project) {
            return new DefaultAntBuilderFactory(project, new DefaultAntLoggingAdapterFactory());
        }
    }
}
