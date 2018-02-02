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

package org.gradle.caching.internal.controller;

import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;

public class RootBuildCacheConfigurationRef {

    private BuildCacheConfigurationInternal buildCacheConfiguration;

    public void set(BuildCacheConfigurationInternal buildCacheConfiguration) {
        // This instance ends up in build/gradle scoped services for nesteds
        // We don't want to invoke close at that time.
        // Instead, close it at the root.
        this.buildCacheConfiguration = buildCacheConfiguration;
    }

    public BuildCacheConfigurationInternal getForNonRootBuild() {
        if (!isSet()) {
            throw new IllegalStateException("Root build cache configuration not yet assigned");
        }

        return buildCacheConfiguration;
    }

    public boolean isSet() {
        return buildCacheConfiguration != null;
    }
}
