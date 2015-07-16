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

package org.gradle.jvm.internal.services;

import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DelegatingComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.resolve.JvmLocalLibraryDependencyResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.jvm.internal.DefaultJavaPlatformVariantDimensionSelector;
import org.gradle.jvm.internal.JarBinaryRenderer;
import org.gradle.jvm.internal.model.JarBinarySpecSpecializationSchemaExtractionStrategy;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.internal.model.DefaultVariantDimensionSelectorFactory;
import org.gradle.language.base.internal.model.VariantDimensionSelectorFactory;
import org.gradle.language.base.internal.resolve.DependentSourceSetResolveContext;

public class PlatformJvmServices implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(JarBinaryRenderer.class);
        registration.add(JarBinarySpecSpecializationSchemaExtractionStrategy.class);
        registration.add(VariantDimensionSelectorFactory.class, DefaultVariantDimensionSelectorFactory.of(JavaPlatform.class, new DefaultJavaPlatformVariantDimensionSelector()));
    }

    public void registerBuildSessionServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private class BuildScopeServices {
        LocalLibraryDependencyResolverFactory createResolverProviderFactory(ProjectModelResolver projectModelResolver, ServiceRegistry registry) {
            return new LocalLibraryDependencyResolverFactory(projectModelResolver, registry);
        }
    }

    public static class LocalLibraryDependencyResolverFactory implements ResolverProviderFactory {
        private final ProjectModelResolver projectModelResolver;
        private final ServiceRegistry registry;

        public LocalLibraryDependencyResolverFactory(ProjectModelResolver projectModelResolver, ServiceRegistry registry) {
            this.projectModelResolver = projectModelResolver;
            this.registry = registry;
        }

        @Override
        public boolean canCreate(ResolveContext context) {
            return context instanceof DependentSourceSetResolveContext;
        }

        @Override
        public ComponentResolvers create(ResolveContext context) {
            JvmLocalLibraryDependencyResolver delegate = new JvmLocalLibraryDependencyResolver(projectModelResolver,
                ((DependentSourceSetResolveContext) context).getVariants(),
                registry.getAll(VariantDimensionSelectorFactory.class));
            return DelegatingComponentResolvers.of(delegate);
        }
    }

}
