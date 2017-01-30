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

import org.gradle.api.Incubating;
import org.gradle.caching.BuildCacheService;

/**
 * Service builder for build cache services.
 *
 * @param <T> the type of the supported build cache.
 *
 * @since 3.5
 */
@Incubating
public interface BuildCacheServiceBuilder<T extends BuildCache> {
    /**
     * Returns the configuration object used to configure the build cache service.
     * @return
     */
    T getConfiguration();

    /**
     * Builds the build cache service.
     *
     * @return the configured service, or {code null} if configuration says the service should not be enabled.
     */
    BuildCacheService build();
}
