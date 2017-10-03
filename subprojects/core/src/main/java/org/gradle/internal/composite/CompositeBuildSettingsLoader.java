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
import org.gradle.api.internal.SettingsInternal;
import org.gradle.composite.internal.IncludedBuildRegistry;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.SettingsLoader;

import java.io.File;

public class CompositeBuildSettingsLoader implements SettingsLoader {
    private final SettingsLoader delegate;
    private final NestedBuildFactory nestedBuildFactory;
    private final IncludedBuildRegistry includedBuildRegistry;

    public CompositeBuildSettingsLoader(SettingsLoader delegate, NestedBuildFactory nestedBuildFactory, IncludedBuildRegistry includedBuildRegistry) {
        this.delegate = delegate;
        this.nestedBuildFactory = nestedBuildFactory;
        this.includedBuildRegistry = includedBuildRegistry;
    }

    @Override
    public SettingsInternal findAndLoadSettings(GradleInternal gradle) {
        SettingsInternal settings = delegate.findAndLoadSettings(gradle);

        // Add all included builds from the command-line
        for (File file : gradle.getStartParameter().getIncludedBuilds()) {
            includedBuildRegistry.addExplicitBuild(file, nestedBuildFactory);
        }

        // Lock-in explicitly included builds
        includedBuildRegistry.validateExplicitIncludedBuilds(settings);

        return settings;
    }
}
