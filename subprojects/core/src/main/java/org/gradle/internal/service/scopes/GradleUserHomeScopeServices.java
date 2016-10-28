/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;

/**
 * Defines the shared services scoped to a particular Gradle user home directory. These services are reused across multiple builds and operations.
 */
public class GradleUserHomeScopeServices {
    private final ServiceRegistry globalServices;

    public GradleUserHomeScopeServices(ServiceRegistry globalServices) {
        this.globalServices = globalServices;
    }

    public void configure(ServiceRegistration registration, GradleUserHomeDirProvider userHomeDirProvider) {
        registration.addProvider(new CacheRepositoryServices(userHomeDirProvider.getGradleUserHomeDirectory(), null));
        for (GradleUserHomeScopePluginServices plugin : globalServices.getAll(GradleUserHomeScopePluginServices.class)) {
            plugin.registerGradleUserHomeServices(registration);
        }
    }

    CachedClasspathTransformer createCachedClasspathTransformer(CacheRepository cacheRepository, ServiceRegistry serviceRegistry) {
        return new DefaultCachedClasspathTransformer(cacheRepository, new JarCache(), serviceRegistry.getAll(CachedJarFileStore.class));
    }
}
