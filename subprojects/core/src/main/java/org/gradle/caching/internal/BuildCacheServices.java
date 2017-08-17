/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal;

import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.DirectoryBuildCacheFileStoreFactory;
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.DefaultPathKeyFileStore;
import org.gradle.internal.resource.local.PathKeyFileStore;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;

import java.io.File;
import java.util.List;

/**
 * Build scoped services for build cache usage.
 */
public final class BuildCacheServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {

            BuildCacheConfigurationInternal createBuildCacheConfiguration(
                Instantiator instantiator,
                List<BuildCacheServiceRegistration> allBuildCacheServiceFactories
            ) {
                return instantiator.newInstance(DefaultBuildCacheConfiguration.class, instantiator, allBuildCacheServiceFactories);
            }

            DirectoryBuildCacheFileStoreFactory createDirectoryBuildCacheFileStoreFactory() {
                return new DirectoryBuildCacheFileStoreFactory() {
                    @Override
                    public PathKeyFileStore createFileStore(File baseDir) {
                        return new DefaultPathKeyFileStore(baseDir);
                    }
                };
            }

            BuildCacheServiceRegistration createDirectoryBuildCacheServiceRegistration() {
                return new DefaultBuildCacheServiceRegistration(DirectoryBuildCache.class, DirectoryBuildCacheServiceFactory.class);
            }

        });
    }

}
