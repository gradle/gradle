/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.SettingsLoader;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.RootBuildState;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChildBuildRegisteringSettingsLoader implements SettingsLoader {

    private final SettingsLoader delegate;
    private final BuildStateRegistry buildRegistry;

    private final BuildIncluder buildIncluder;

    public ChildBuildRegisteringSettingsLoader(SettingsLoader delegate, BuildStateRegistry buildRegistry, BuildIncluder buildIncluder) {
        this.delegate = delegate;
        this.buildRegistry = buildRegistry;
        this.buildIncluder = buildIncluder;
    }

    @Override
    public SettingsInternal findAndLoadSettings(GradleInternal gradle) {
        SettingsInternal settings = delegate.findAndLoadSettings(gradle);

        // Add included builds defined in settings
        List<IncludedBuildSpec> includedBuilds = settings.getIncludedBuilds();
        if (!includedBuilds.isEmpty()) {
            Set<IncludedBuildInternal> children = new LinkedHashSet<>(includedBuilds.size());
            RootBuildState rootBuild = buildRegistry.getRootBuild();
            for (IncludedBuildSpec includedBuildSpec : includedBuilds) {
                if (!includedBuildSpec.rootDir.equals(rootBuild.getBuildRootDir())) {
                    IncludedBuildState includedBuild = buildIncluder.includeBuild(includedBuildSpec);
                    children.add(includedBuild.getModel());
                } else {
                    buildRegistry.registerSubstitutionsForRootBuild();
                    children.add(buildRegistry.getRootBuild().getModel());
                }
            }

            // Set the visible included builds
            gradle.setIncludedBuilds(children);
        } else {
            gradle.setIncludedBuilds(Collections.emptyList());
        }

        return settings;
    }

}
