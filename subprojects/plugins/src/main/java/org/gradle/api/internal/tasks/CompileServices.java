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
import org.gradle.api.internal.jvm.JvmBinaryRenderer;
import org.gradle.api.internal.tasks.compile.incremental.cache.DefaultGeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.DefaultUserHomeScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.UserHomeScopedCompileCaches;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.WellKnownFileLocations;

public class CompileServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(JvmBinaryRenderer.class);
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.addProvider(new GradleScopeCompileServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new UserHomeScopeServices());
    }

    private static class GradleScopeCompileServices {
        void configure(ServiceRegistration registration, JdkToolsInitializer initializer) {
            // Hackery
            initializer.initializeJdkTools();
        }

        DefaultGeneralCompileCaches createGeneralCompileCaches(CacheRepository cacheRepository, Gradle gradle, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, UserHomeScopedCompileCaches userHomeScopedCompileCaches, WellKnownFileLocations wellKnownFileLocations, FileSystemSnapshotter fileSystemSnapshotter, StringInterner interner) {
            return new DefaultGeneralCompileCaches(fileSystemSnapshotter, userHomeScopedCompileCaches, cacheRepository, gradle, inMemoryCacheDecoratorFactory, wellKnownFileLocations, interner);
        }
    }

    private class UserHomeScopeServices {
        DefaultUserHomeScopedCompileCaches createCompileCaches(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, FileSystemSnapshotter fileSystemSnapshotter, StringInterner interner) {
            return new DefaultUserHomeScopedCompileCaches(fileSystemSnapshotter, cacheRepository, inMemoryCacheDecoratorFactory, interner);
        }
    }
}
