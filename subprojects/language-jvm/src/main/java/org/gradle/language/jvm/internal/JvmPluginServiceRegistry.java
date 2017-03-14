/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.jvm.internal;

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

public class JvmPluginServiceRegistry implements PluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ComponentRegistrationAction());
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.addProvider(new GradleScopeJvmPluginServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class ComponentRegistrationAction {
        /***
         * @param registration unused parameter required by convention, see {@link org.gradle.internal.service.DefaultServiceRegistry}.
         */
        public void configure(ServiceRegistration registration,
                              ComponentTypeRegistry componentTypeRegistry) {
            componentTypeRegistry
                .maybeRegisterComponentType(JvmLibrary.class)
                .registerArtifactType(SourcesArtifact.class, ArtifactType.SOURCES);
        }
    }

    private static class GradleScopeJvmPluginServices {
        InProcessCompilerDaemonFactory createInProcessCompilerDaemonFactory(ClassLoaderFactory classLoaderFactory, Gradle gradle) {
            return new InProcessCompilerDaemonFactory(classLoaderFactory, gradle.getGradleUserHomeDir());
        }
    }
}
