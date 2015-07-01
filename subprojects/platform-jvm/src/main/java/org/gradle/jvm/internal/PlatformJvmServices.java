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

package org.gradle.jvm.internal;

import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RequestScopeResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProvider;
import org.gradle.api.internal.resolve.LocalLibraryDependencyResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.internal.resolve.DependentSourceSetResolveContext;

public class PlatformJvmServices implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(JarBinaryRenderer.class);
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildScopeServices {
        RequestScopeResolverProviderFactory.Query createResolverProvider(ProjectModelResolver projectModelResolver) {
            return new RequestScopeResolverProviderFactory.Query(JavaLibraryResolverProvider.class, projectModelResolver) {
                @Override
                public boolean canCreateFrom(ResolveContext context) {
                    return context instanceof DependentSourceSetResolveContext;
                }
            };
        }

    }

    public static class JavaLibraryResolverProvider implements ResolverProvider {
        private final LocalLibraryDependencyResolver resolver;

        public JavaLibraryResolverProvider(ResolveContext context, ProjectModelResolver projectModelResolver) {
            resolver = new LocalLibraryDependencyResolver(projectModelResolver, (JavaPlatform) ((DependentSourceSetResolveContext) context).getPlatform());
        }

        @Override
        public DependencyToComponentIdResolver getComponentIdResolver() {
            return resolver;
        }

        @Override
        public ComponentMetaDataResolver getComponentResolver() {
            return resolver;
        }

        @Override
        public ArtifactResolver getArtifactResolver() {
            return resolver;
        }
    }
}
