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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.project.taskfactory.ClasspathPropertyAnnotationHandler;
import org.gradle.api.internal.project.taskfactory.CompileClasspathPropertyAnnotationHandler;
import org.gradle.api.internal.resolve.DefaultLocalLibraryResolver;
import org.gradle.api.internal.resolve.LocalLibraryDependencyResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.api.internal.resolve.VariantBinarySelector;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.internal.JarBinaryRenderer;
import org.gradle.jvm.internal.resolve.DefaultJavaPlatformVariantAxisCompatibility;
import org.gradle.jvm.internal.resolve.DefaultLibraryResolutionErrorMessageBuilder;
import org.gradle.jvm.internal.resolve.DefaultVariantAxisCompatibilityFactory;
import org.gradle.jvm.internal.resolve.JvmLibraryResolveContext;
import org.gradle.jvm.internal.resolve.JvmLocalLibraryMetaDataAdapter;
import org.gradle.jvm.internal.resolve.JvmVariantSelector;
import org.gradle.jvm.internal.resolve.VariantAxisCompatibilityFactory;
import org.gradle.jvm.internal.resolve.VariantsMetaData;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.process.internal.ExecActionFactory;

import java.util.List;

public class PlatformJvmServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(JarBinaryRenderer.class);
        registration.add(VariantAxisCompatibilityFactory.class, DefaultVariantAxisCompatibilityFactory.of(JavaPlatform.class, new DefaultJavaPlatformVariantAxisCompatibility()));
        registration.add(ClasspathPropertyAnnotationHandler.class);
        registration.add(CompileClasspathPropertyAnnotationHandler.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    private class BuildScopeServices {
        LocalLibraryDependencyResolverFactory createResolverProviderFactory(ProjectModelResolver projectModelResolver, ModelSchemaStore schemaStore, List<VariantAxisCompatibilityFactory> factories) {
            return new LocalLibraryDependencyResolverFactory(projectModelResolver, schemaStore, factories);
        }

        JavaInstallationProbe createJavaInstallationProbe(ExecActionFactory factory) {
            return new JavaInstallationProbe(factory);
        }
    }

    public static class LocalLibraryDependencyResolverFactory implements ResolverProviderFactory {
        private final ProjectModelResolver projectModelResolver;
        private final ModelSchemaStore schemaStore;
        private final List<VariantAxisCompatibilityFactory> factories;

        public LocalLibraryDependencyResolverFactory(ProjectModelResolver projectModelResolver, ModelSchemaStore schemaStore, List<VariantAxisCompatibilityFactory> factories) {
            this.projectModelResolver = projectModelResolver;
            this.schemaStore = schemaStore;
            this.factories = factories;
        }

        @Override
        public boolean canCreate(ResolveContext context) {
            return context instanceof JvmLibraryResolveContext;
        }

        @Override
        public ComponentResolvers create(ResolveContext context) {
            VariantsMetaData variants = ((JvmLibraryResolveContext) context).getVariants();
            VariantBinarySelector variantSelector = new JvmVariantSelector(factories, JvmBinarySpec.class, schemaStore, variants);
            JvmLocalLibraryMetaDataAdapter libraryMetaDataAdapter = new JvmLocalLibraryMetaDataAdapter();
            return new LocalLibraryDependencyResolver(
                    JvmBinarySpec.class,
                    projectModelResolver,
                    new DefaultLocalLibraryResolver(),
                    variantSelector,
                    libraryMetaDataAdapter,
                    new DefaultLibraryResolutionErrorMessageBuilder(variants, schemaStore)
            );
        }
    }
}
