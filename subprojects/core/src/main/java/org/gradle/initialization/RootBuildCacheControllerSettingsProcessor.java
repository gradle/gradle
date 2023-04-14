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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.RootBuildCacheControllerRef;

public class RootBuildCacheControllerSettingsProcessor implements SettingsProcessor {

    public static void process(GradleInternal gradle) {
        // The strategy for sharing build cache configuration across included builds in a composite,
        // requires that the cache configuration be finalized (and cache controller available)
        // before configuring them. This achieves that.
        if (gradle.isRootBuild()) {
            BuildCacheController rootController = gradle.getServices().get(BuildCacheController.class);
            RootBuildCacheControllerRef rootControllerRef = gradle.getServices().get(RootBuildCacheControllerRef.class);
            rootControllerRef.set(rootController);
        }
    }

    private final SettingsProcessor delegate;

    public RootBuildCacheControllerSettingsProcessor(SettingsProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public SettingsState process(GradleInternal gradle, SettingsLocation settingsLocation, ClassLoaderScope buildRootClassLoaderScope, StartParameter startParameter) {
        SettingsState state = delegate.process(gradle, settingsLocation, buildRootClassLoaderScope, startParameter);
        process(gradle);
        return state;
    }
}
