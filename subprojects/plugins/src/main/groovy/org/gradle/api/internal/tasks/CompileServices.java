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

import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.cache.*;
import org.gradle.api.internal.tasks.compile.incremental.jar.DefaultJarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;
import org.gradle.cache.CacheRepository;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;

public class CompileServices implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeCompileServices());
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildScopeCompileServices {
        void configure(ServiceRegistration registration, JdkToolsInitializer initializer) {
            // Hackery
            initializer.initializeJdkTools();
        }

        InProcessCompilerDaemonFactory createInProcessCompilerDaemonFactory() {
            return new InProcessCompilerDaemonFactory();
        }

        GeneralCompileCaches createGeneralCompileCaches(ClassAnalysisCache classAnalysisCache, JarSnapshotCache jarSnapshotCache) {
            return new DefaultGeneralCompileCaches(classAnalysisCache, jarSnapshotCache);
        }

        ClassAnalysisCache createClassAnalysisCache(CacheRepository cacheRepository) {
            return new DefaultClassAnalysisCache(cacheRepository);
        }

        JarSnapshotCache createJarSnapshotCache(CacheRepository cacheRepository) {
            return new DefaultJarSnapshotCache(cacheRepository);
        }
    }
}
