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

import org.gradle.api.artifacts.ResolvedModuleVersion;

public interface CachedDynamicVersion {
    /**
     * @return The module resolved for the dynamic version.
     */
    ResolvedModuleVersion getModule();

    /**
     * @return The age of the cached entry, in milliseconds.
     */
    long getAgeMillis();

    /**
     * Call this method if you want to invalidate the cached version, forcing resolution of this dynamic version.
     */
    void mustCheckForUpdates();
}
