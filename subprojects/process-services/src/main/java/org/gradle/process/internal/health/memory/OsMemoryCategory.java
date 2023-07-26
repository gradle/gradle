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
public interface OsMemoryCategory extends Named {
    /**
     * Interface for limited memory. Total and free values are available.
     */
    @NonNullApi
    interface Limited extends OsMemoryCategory {
        /**
         * Get the total memory of this kind in bytes.
         *
         * @return the total memory of this kind in bytes
         */
        long getTotal();

        /**
         * Get the free memory of this kind in bytes.
         *
         * @return the free memory of this kind in bytes
         */
        long getFree();
    }

    /**
     * Marker interface for unknown memory.
     */
    @NonNullApi
    interface Unknown extends OsMemoryCategory {}
}
