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

package org.gradle.api.cache;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Represents the configuration of a given type of cache resource.
 *
 * @since 8.0
 */
@Incubating
@HasInternalProtocol
public interface CacheResourceConfiguration {
    /**
     * Sets the interval (in days) after which unused entries will be considered stale and removed from the cache.
     * Any entries not used within this interval will be candidates for eviction.
     */
    void setRemoveUnusedEntriesAfterDays(int removeUnusedEntriesAfterDays);
}
