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
import org.gradle.api.internal.resolve.DefaultLibraryResolutionErrorMessageBuilder;
import org.gradle.api.internal.resolve.JvmLocalLibraryMetaDataAdapter;
import org.gradle.api.internal.resolve.LocalLibraryDependencyResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.internal.DefaultJavaPlatformVariantAxisCompatibility;
import org.gradle.jvm.internal.JarBinaryRenderer;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe;
import org.gradle.language.base.internal.model.DefaultVariantAxisCompatibilityFactory;
import org.gradle.language.base.internal.model.VariantAxisCompatibilityFactory;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.resolve.LocalComponentResolveContext;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.process.internal.ExecActionFactory;

public class PlatformJvmServices implements PluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(JarBinaryRenderer.class);
        registration.add(VariantAxisCompatibilityFactory.class, DefaultVariantAxisCompatibilityFactory.of(JavaPlatform.class, new DefaultJavaPlatformVariantAxisCompatibility()));
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
    }

    private class BuildScopeServices {
        LocalLibraryDependencyResolverFactory createResolverProviderFactory(ProjectModelResolver projectModelResolver, ServiceRegistry registry) {
            return new LocalLibraryDependencyResolverFactory(projectModelResolver, registry);
        }

        JavaInstallationProbe createJavaInstallationProbe(ExecActionFactory factory) {
            return new JavaInstallationProbe(factory);
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
            return context instanceof LocalComponentResolveContext;
        }

        @Override
        public ComponentResolvers create(ResolveContext context) {
            final ModelSchemaStore schemaStore = registry.get(ModelSchemaStore.class);
            VariantsMetaData variants = ((LocalComponentResolveContext) context).getVariants();
            JvmLocalLibraryMetaDataAdapter libraryMetaDataAdapter = new JvmLocalLibraryMetaDataAdapter();
            LocalLibraryDependencyResolver<JvmBinarySpec> delegate =
                    new LocalLibraryDependencyResolver<JvmBinarySpec>(
                            JvmBinarySpec.class,
                            projectModelResolver,
                            registry.getAll(VariantAxisCompatibilityFactory.class),
                            variants,
                            schemaStore,
                            libraryMetaDataAdapter,
                            new DefaultLibraryResolutionErrorMessageBuilder(variants, schemaStore)
                    );
            return DelegatingComponentResolvers.of(delegate);
        }
    }
}
