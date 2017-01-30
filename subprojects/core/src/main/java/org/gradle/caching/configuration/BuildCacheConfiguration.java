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
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Configuration for the build cache for an entire Gradle build.
 *
 * <p>It consists of a {@code local} and a {@code remote} part that can be configured separately. When both are enabled,
 * both get queried for pulling and pushing. When pulling first {@code local} is queried, and then {@code remote}.
 * When pushing, first {@code remote} is tried then {@code local}.</p>
 *
 * <p>The local part is pre-configured to be a {@link LocalBuildCache}. The remote part can be configured by specifying
 * the type of cache service to use. Remote cache services can be registered via the {@link BuildCacheServiceFactory} SPI.</p>
 *
 * @since 3.5
 */
@Incubating
@HasInternalProtocol
public interface BuildCacheConfiguration {
    /**
     * Returns the local cache configuration.
     */
    LocalBuildCache getLocal();

    /**
     * Executes the given action against the local configuration.
     *
     * @param configuration the action to execute against the local cache configuration.
     */
    void local(Action<? super LocalBuildCache> configuration);

    /**
     * Configures a remote cache with the given type. If a remote cache was already configured, it gets overridden completely.
     *
     * @param type the type of remote cache to configure.
     */
    <T extends BuildCache> T remote(Class<T> type);

    /**
     * Configures a remote cache with the given type. If a remote cache was already configured, it gets overridden completely.
     *
     * @param type the type of remote cache to configure.
     * @param configuration the configuration to execute against the remote cache.
     */
    <T extends BuildCache> T remote(Class<T> type, Action<? super T> configuration);

    /**
     * Returns the remote cache configuration.
     */
    BuildCache getRemote();

    /**
     * Executes the given action against the currently configured remote cache.
     *
     * @param configuration the action to execute against the currently configured remote cachce.
     */
    void remote(Action<? super BuildCache> configuration);
}
