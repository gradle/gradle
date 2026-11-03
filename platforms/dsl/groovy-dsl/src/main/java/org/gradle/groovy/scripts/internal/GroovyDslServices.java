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

package org.gradle.groovy.scripts.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.ImportsReader;
import org.gradle.configuration.project.DefaultCompileOperationFactory;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.scripts.ScriptExecutionListener;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

/**
 * Registers the services that compile and run Groovy DSL scripts.
 */
public class GroovyDslServices extends AbstractGradleModuleServices {

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.add(GroovyDslWorkspaceProvider.class);
        registration.addProvider(new GradleUserHomeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildServices());
    }

    private static class GradleUserHomeServices implements ServiceRegistrationProvider {
        @Provides
        CrossBuildInMemoryCachingScriptClassCache createCachingScriptCompiler(CrossBuildInMemoryCacheFactory cacheFactory) {
            return new CrossBuildInMemoryCachingScriptClassCache(cacheFactory);
        }
    }

    private static class BuildServices implements ServiceRegistrationProvider {
        @Provides
        ScriptCompilerFactory createScriptCompileFactory(
            GroovyScriptClassCompiler scriptCompiler,
            CrossBuildInMemoryCachingScriptClassCache cache,
            ScriptRunnerFactory scriptRunnerFactory
        ) {
            return new DefaultScriptCompilerFactory(
                new BuildScopeInMemoryCachingScriptClassCompiler(cache, scriptCompiler),
                scriptRunnerFactory
            );
        }

        @Provides
        GroovyScriptClassCompiler createFileCacheBackedScriptClassCompiler(
            BuildOperationRunner buildOperationRunner,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            DefaultScriptCompilationHandler scriptCompilationHandler,
            CachedClasspathTransformer classpathTransformer,
            ExecutionEngine executionEngine,
            FileCollectionFactory fileCollectionFactory,
            InputFingerprinter inputFingerprinter,
            GroovyDslWorkspaceProvider groovyDslWorkspaceProvider,
            ClasspathElementTransformFactoryForLegacy transformFactoryForLegacy,
            GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry,
            PropertyUpgradeReportConfig propertyUpgradeReportConfig
        ) {
            return new GroovyScriptClassCompiler(
                new BuildOperationBackedScriptCompilationHandler(scriptCompilationHandler, buildOperationRunner),
                classLoaderHierarchyHasher,
                classpathTransformer,
                executionEngine,
                fileCollectionFactory,
                inputFingerprinter,
                groovyDslWorkspaceProvider.getWorkspace(),
                transformFactoryForLegacy,
                gradleCoreTypeRegistry,
                propertyUpgradeReportConfig
            );
        }

        @Provides
        CompileOperationFactory createCompileOperationFactory(DocumentationRegistry documentationRegistry) {
            return new DefaultCompileOperationFactory(documentationRegistry);
        }

        @Provides
        DefaultScriptCompilationHandler createScriptCompilationHandler(ImportsReader importsReader, ObjectFactory objectFactory) {
            return objectFactory.newInstance(DefaultScriptCompilationHandler.class, importsReader);
        }

        @Provides
        ScriptRunnerFactory createScriptRunnerFactory(
            ScriptExecutionListener scriptExecutionListener,
            InstantiatorFactory instantiatorFactory
        ) {
            return new DefaultScriptRunnerFactory(
                scriptExecutionListener,
                instantiatorFactory.inject()
            );
        }
    }
}
