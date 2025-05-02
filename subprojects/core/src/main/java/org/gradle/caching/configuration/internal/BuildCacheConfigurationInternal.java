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

package org.gradle.caching.configuration.internal;

import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@ServiceScope(Scope.Build.class)
public interface BuildCacheConfigurationInternal extends BuildCacheConfiguration {
    /**
     * Finds a build cache implementation factory class for the given configuration type.
     */
    <T extends BuildCache> Class<? extends BuildCacheServiceFactory<T>> getBuildCacheServiceFactoryType(Class<T> configurationType);

    /**
     * Replaces local directory build cache.
     */
    void setLocal(DirectoryBuildCache local);

    /**
     * Replaces remote build cache.
     */
    void setRemote(@Nullable BuildCache remote);

    /**
     * Gets build cache service registrations
     */
    Set<BuildCacheServiceRegistration> getRegistrations();

    /**
     * Replaces build cache service registrations
     */
    void setRegistrations(Set<BuildCacheServiceRegistration> registrations);
}
