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

package org.gradle.process.internal.health.memory;

import com.google.common.base.Function;

/**
 * OS memory status. This is not a live view, so it may be stored and used later.
 */
public interface OsMemoryStatus {
    /**
     * Get the total physical memory on the system, in bytes.
     *
     * @return the total physical memory on the system
     */
    long getTotalPhysicalMemory();

    /**
     * Given a function to compute the intended free memory, compute the amount of memory that should be reclaimed.
     *
     * <p>
     * The function is given the total amount of memory on the system and should return the amount of memory that
     * should be free. It may be called multiple times.
     * </p>
     *
     * @param computeIntendedFreeMemory a function to compute the intended free memory
     */
    MemoryReclaim computeMemoryReclaimAmount(Function<Long, Long> computeIntendedFreeMemory);
}
