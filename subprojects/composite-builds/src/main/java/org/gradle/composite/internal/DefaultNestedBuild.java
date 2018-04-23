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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.BuildIdentity;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.util.Path;

class DefaultNestedBuild implements NestedBuildState {
    private final SettingsInternal settings;

    DefaultNestedBuild(SettingsInternal settings) {
        this.settings = settings;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return settings.getGradle().getServices().get(BuildIdentity.class).getCurrentBuild();
    }

    @Override
    public boolean isImplicitBuild() {
        return true;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return settings;
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return settings.getGradle().getRootProject().getProjectRegistry().getProject(projectPath.getPath()).getIdentityPath();
    }
}
