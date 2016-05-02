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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetaData;

import java.util.List;

public class DefaultProjectComponentRegistry implements ProjectComponentRegistry {
    private final List<ProjectComponentProvider> providers;

    public DefaultProjectComponentRegistry(List<ProjectComponentProvider> providers) {
        this.providers = providers;
    }

    @Override
    public LocalComponentMetaData getProject(ProjectComponentIdentifier projectIdentifier) {
        for (ProjectComponentProvider provider : providers) {
            LocalComponentMetaData componentMetaData = provider.getProject(projectIdentifier);
            if (componentMetaData != null) {
                return componentMetaData;
            }
        }
        return null;
    }
}
