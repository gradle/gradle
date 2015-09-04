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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.internal.artifacts.ivyservice.LocalComponentConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationBackedComponent;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.internal.component.local.model.LocalComponentMetaData;

public class DefaultProjectComponentRegistry implements ProjectComponentRegistry {
    private final LocalComponentConverter localComponentConverter;
    private final ProjectRegistry<ProjectInternal> projectRegistry;

    public DefaultProjectComponentRegistry(LocalComponentConverter localComponentConverter, ProjectRegistry<ProjectInternal> projectRegistry) {
        this.localComponentConverter = localComponentConverter;
        this.projectRegistry = projectRegistry;
    }

    public LocalComponentMetaData getProject(String projectPath) {
        ProjectInternal project = projectRegistry.getProject(projectPath);
        if (project == null) {
            return null;
        }
        return localComponentConverter.convert(new ConfigurationBackedComponent(project.getModule(), project.getConfigurations()));
    }
}
