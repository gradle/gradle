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
         * The number of bytes of the loaded cache artifact if it was a hit.
         * Else undetermined.
         */
        long getCacheEntrySize();

        /**
         * If work was UP_TO_DATE or FROM_CACHE, this will convey the ID of the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the work executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        @Nullable
        String getOriginBuildInvocationId();
    }

}
