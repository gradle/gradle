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

package org.gradle.initialization;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;

class SettingsAttachingSettingsLoader implements SettingsLoader {
    private final SettingsLoader delegate;
    private final ProjectStateRegistry projectRegistry;

    SettingsAttachingSettingsLoader(SettingsLoader delegate, ProjectStateRegistry projectRegistry) {
        this.delegate = delegate;
        this.projectRegistry = projectRegistry;
    }

    @Override
    public SettingsState findAndLoadSettings(GradleInternal gradle) {
        SettingsState state = delegate.findAndLoadSettings(gradle);
        gradle.attachSettings(state);
        projectRegistry.registerProjects(gradle.getOwner(), state.getSettings().getProjectRegistry());
        return state;
    }
}
