/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.UserHomeScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.classpath.CachingClassSetAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClassSetAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.DefaultClassSetAnalyzer;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.internal.vfs.FileSystemAccess;

public class CompileServices extends AbstractGradleModuleServices {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeCompileServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new UserHomeScopeServices());
    }

    private static class BuildScopeCompileServices implements ServiceRegistrationProvider {
        void configure(ServiceRegistration registration, JdkToolsInitializer initializer) {
            // Hackery
            initializer.initializeJdkTools();
        }

        @Provides
        public IncrementalCompilerFactory createIncrementalCompilerFactory(BuildOperationExecutor buildOperationExecutor, StringInterner interner, ClassSetAnalyzer classSetAnalyzer) {
            return new IncrementalCompilerFactory(buildOperationExecutor, interner, classSetAnalyzer);
        }

        @Provides
        CachingClassDependenciesAnalyzer createClassAnalyzer(StringInterner interner, GeneralCompileCaches cache) {
            return new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(interner), cache.getClassAnalysisCache());
        }

        @Provides
        CachingClassSetAnalyzer createClassSetAnalyzer(FileHasher fileHasher, StreamHasher streamHasher, ClassDependenciesAnalyzer classAnalyzer,
                                                       FileOperations fileOperations, FileSystemAccess fileSystemAccess, GeneralCompileCaches cache) {
            return new CachingClassSetAnalyzer(
                new DefaultClassSetAnalyzer(fileHasher, streamHasher, classAnalyzer, fileOperations),
                fileSystemAccess,
                cache.getClassSetAnalysisCache()
            );
        }
    }

    private static class UserHomeScopeServices implements ServiceRegistrationProvider {
        @Provides
        UserHomeScopedCompileCaches createCompileCaches(GlobalScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, StringInterner interner) {
            return new UserHomeScopedCompileCaches(cacheBuilderFactory, inMemoryCacheDecoratorFactory, interner);
        }
    }
}
