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

package org.gradle.internal.composite;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.SettingsLoader;
import org.gradle.initialization.SettingsState;
import org.gradle.internal.build.BuildStateRegistry;

public class CompositeBuildSettingsLoader implements SettingsLoader {
    private final SettingsLoader delegate;
    private final BuildStateRegistry buildRegistry;

    public CompositeBuildSettingsLoader(SettingsLoader delegate, BuildStateRegistry buildRegistry) {
        this.delegate = delegate;
        this.buildRegistry = buildRegistry;
    }

    @Override
    public SettingsState findAndLoadSettings(GradleInternal gradle) {
        SettingsState settings = delegate.findAndLoadSettings(gradle);

        // Lock-in explicitly included builds
        buildRegistry.finalizeIncludedBuilds();

        return settings;
    }
}
