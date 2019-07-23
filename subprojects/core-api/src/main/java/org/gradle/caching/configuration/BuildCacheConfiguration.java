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

package org.gradle.caching.configuration;

import org.gradle.api.Action;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * Configuration for the <a href="https://docs.gradle.org/current/userguide/build_cache.html" target="_top">build cache</a> for an entire Gradle build.
 *
 * @since 3.5
 */
@HasInternalProtocol
@SuppressWarnings("deprecation")
public interface BuildCacheConfiguration extends CompatibilitySupportForBuildCacheConfiguration {

    /**
     * Registers a custom build cache type.
     *
     * @param configurationType Configuration type used to provide parameters to a {@link org.gradle.caching.BuildCacheService}
     * @param buildCacheServiceFactoryType Implementation type of {@link BuildCacheServiceFactory} that is used to create a {@code BuildCacheService}
     */
    <T extends BuildCache> void registerBuildCacheService(Class<T> configurationType, Class<? extends BuildCacheServiceFactory<? super T>> buildCacheServiceFactoryType);

    /**
     * Returns the local directory cache configuration.
     */
    DirectoryBuildCache getLocal();

    /**
     * Configures the local cache with the given type.
     *
     * <p><strong>Note:</strong> using any type except {@link org.gradle.caching.local.DirectoryBuildCache} is invalid.</p>
     *
     * @param type the type of local cache to configure.
     *
     * @deprecated Use {@link #getLocal()} instead.
     */
    @Deprecated
    <T extends DirectoryBuildCache> T local(Class<T> type);

    /**
     * Configures the local cache with the given type.
     *
     * <p><strong>Note:</strong> using any type except {@link org.gradle.caching.local.DirectoryBuildCache} is invalid.</p>
     *
     * @param type the type of local cache to configure.
     * @param configuration the configuration to execute against the remote cache.
     *
     * @deprecated Use {@link #local(Action)} instead.
     */
    @Deprecated
    <T extends DirectoryBuildCache> T local(Class<T> type, Action<? super T> configuration);

    /**
     * Executes the given action against the local configuration.
     *
     * @param configuration the action to execute against the local cache configuration.
     */
    void local(Action<? super DirectoryBuildCache> configuration);

    /**
     * Returns the remote cache configuration.
     */
    @Nullable
    BuildCache getRemote();

    /**
     * Configures a remote cache with the given type.
     * <p>
     * If a remote build cache has already been configured with a different type, this method replaces it.
     * </p>
     * <p>
     * Storing ("push") in the remote build cache is disabled by default.
     * </p>
     * @param type the type of remote cache to configure.
     *
     */
    <T extends BuildCache> T remote(Class<T> type);

    /**
     * Configures a remote cache with the given type.
     * <p>
     * If a remote build cache has already been configured with a <b>different</b> type, this method replaces it.
     * </p>
     * <p>
     * If a remote build cache has already been configured with the <b>same</b>, this method configures it.
     * </p>
     * <p>
     * Storing ("push") in the remote build cache is disabled by default.
     * </p>
     * @param type the type of remote cache to configure.
     * @param configuration the configuration to execute against the remote cache.
     *
     */
    <T extends BuildCache> T remote(Class<T> type, Action<? super T> configuration);

    /**
     * Executes the given action against the currently configured remote cache.
     *
     * @param configuration the action to execute against the currently configured remote cache.
     *
     * @throws IllegalStateException If no remote cache has been assigned yet
     */
    void remote(Action<? super BuildCache> configuration);
}
