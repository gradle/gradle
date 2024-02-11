/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.configurationcache;

import org.gradle.internal.operations.BuildOperationType;

import javax.annotation.Nullable;

/**
 * Details about a configuration cache load build operation.
 *
 * @since 7.5
 */
public class ConfigurationCacheLoadBuildOperationType implements BuildOperationType<ConfigurationCacheLoadBuildOperationType.Details, ConfigurationCacheLoadBuildOperationType.Result> {

    public interface Details {
    }

    public interface Result {
        /**
         * The number of bytes of the stored configuration cache entry.
         *
         * @since 8.6
         */
        long getCacheEntrySize();

        /**
         * The ID of the build that store the configuration cache entry.
         *
         * `null` when unknown, e.g. when loading models and not a task graph.
         *
         * @since 8.6
         */
        @Nullable
        String getOriginBuildInvocationId();
    }

}
