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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Global.class)
public interface MemoryManager {

    void addListener(JvmMemoryStatusListener listener);

    void addListener(OsMemoryStatusListener listener);

    void removeListener(JvmMemoryStatusListener listener);

    void removeListener(OsMemoryStatusListener listener);

    /**
     * Register a memory resource holder.
     *
     * @param holder The memory resource holder to register
     */
    void addMemoryHolder(MemoryHolder holder);

    /**
     * Unregister a memory resource holder.
     *
     * @param holder The memory resource holder to unregister
     */
    void removeMemoryHolder(MemoryHolder holder);

    /**
     * Request an amount of free system memory.
     *
     * Attempt to free as much memory as possible to get {@literal memoryAmountBytes}
     * of free memory available on the system.
     *
     * @param memoryAmountBytes The requested amount of memory in bytes. If negative, {@literal 0} is assumed.
     */
    void requestFreeMemory(long memoryAmountBytes);
}
