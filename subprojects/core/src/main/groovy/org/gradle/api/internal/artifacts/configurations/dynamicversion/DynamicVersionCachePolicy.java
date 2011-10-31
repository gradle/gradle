/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations.dynamicversion;

import org.gradle.api.artifacts.ResolvedModule;

/**
 * Specifies when we can use cached values for Dynamic Revisions.
 */
public interface DynamicVersionCachePolicy {
    /**
     * Returns whether a cached resolution value for a dynamic revision can be used, or if the dynamic revision should be resolved again to get the latest version.
     * @param module The resolved module
     * @param ageMillis The age of the cache entry in milliseconds
     * @return True if the revision must be resolved again
     */
    boolean mustCheckForUpdates(ResolvedModule module, long ageMillis);
}
