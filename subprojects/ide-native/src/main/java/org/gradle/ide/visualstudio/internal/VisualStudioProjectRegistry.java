/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;

public class VisualStudioProjectRegistry extends DefaultNamedDomainObjectSet<DefaultVisualStudioProject> {
    private final ProjectIdentifier projectIdentifier;
    private final FileResolver fileResolver;
    private final VisualStudioProjectMapper projectMapper;

    public VisualStudioProjectRegistry(ProjectIdentifier projectIdentifier, FileResolver fileResolver, VisualStudioProjectMapper projectMapper, Instantiator instantiator) {
        super(DefaultVisualStudioProject.class, instantiator);
        this.projectIdentifier = projectIdentifier;
        this.fileResolver = fileResolver;
        this.projectMapper = projectMapper;
    }

    public VisualStudioProjectConfiguration getProjectConfiguration(VisualStudioTargetBinary targetBinary) {
        String projectName = projectName(targetBinary);
        return getByName(projectName).getConfiguration(targetBinary);
    }

    public VisualStudioProjectConfiguration addProjectConfiguration(VisualStudioTargetBinary nativeBinary) {
        VisualStudioProjectMapper.ProjectConfigurationNames names = projectMapper.mapToConfiguration(nativeBinary);
        DefaultVisualStudioProject project = getOrCreateProject(nativeBinary.getProjectPath(), names.project, nativeBinary.getComponentName());
        VisualStudioProjectConfiguration configuration = createVisualStudioProjectConfiguration(project, nativeBinary, names.configuration, names.platform);
        project.addConfiguration(nativeBinary, configuration);
        return configuration;
    }

    private VisualStudioProjectConfiguration createVisualStudioProjectConfiguration(VisualStudioProject project, VisualStudioTargetBinary nativeBinary, String configuration, String platform) {
        return getInstantiator().newInstance(VisualStudioProjectConfiguration.class, project, configuration, platform, nativeBinary);
    }

    private DefaultVisualStudioProject getOrCreateProject(String projectPath, String vsProjectName, String componentName) {
        DefaultVisualStudioProject vsProject = findByName(vsProjectName);
        if (vsProject == null) {
            vsProject = getInstantiator().newInstance(DefaultVisualStudioProject.class, new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), vsProjectName), projectPath, componentName, fileResolver, getInstantiator());
            add(vsProject);
        }
        return vsProject;
    }

    private String projectName(VisualStudioTargetBinary nativeBinary) {
        return projectMapper.mapToConfiguration(nativeBinary).project;
    }
}
