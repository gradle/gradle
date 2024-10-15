/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.process.internal.health.memory;

import org.gradle.api.Named;
import org.gradle.api.NonNullApi;

/**
 * Memory status information, usually either physical or virtual.
 */
@NonNullApi
public interface OsMemoryStatusAspect extends Named {
    /**
     * Get the name of this category.
     *
     * @return the name of this category
     */
    @Override
    String getName();

    /**
     * Represents available memory information.
     */
    @NonNullApi
    interface Available extends OsMemoryStatusAspect {
        /**
         * Get the total memory of this category in bytes.
         *
         * @return the total memory of this category in bytes
         */
        long getTotal();

        /**
         * Get the free memory of this category in bytes.
         *
         * @return the free memory of this category in bytes
         */
        long getFree();
    }

    /**
     * Marker interface for unavailable memory information.
     *
     * <p>
     * This is used when a specific memory category is not available on the current platform.
     * For example, we do not track virtual memory on Linux.
     * </p>
     */
    @NonNullApi
    interface Unavailable extends OsMemoryStatusAspect {}
}
