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
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.internal.reflect.Instantiator;

import static org.gradle.ide.visualstudio.internal.VisualStudioProjectMapper.getConfigurationName;
import static org.gradle.ide.visualstudio.internal.VisualStudioProjectMapper.getProjectName;

public class VisualStudioProjectRegistry extends DefaultNamedDomainObjectSet<DefaultVisualStudioProject> {
    private final FileResolver fileResolver;

    public VisualStudioProjectRegistry(FileResolver fileResolver, Instantiator instantiator) {
        super(DefaultVisualStudioProject.class, instantiator);
        this.fileResolver = fileResolver;
    }

    public VisualStudioProjectConfiguration getProjectConfiguration(VisualStudioTargetBinary targetBinary) {
        String projectName = getProjectName(targetBinary);
        return getByName(projectName).getConfiguration(targetBinary);
    }

    public VisualStudioProjectConfiguration addProjectConfiguration(VisualStudioTargetBinary nativeBinary) {
        DefaultVisualStudioProject project = getOrCreateProject(nativeBinary.getProjectPath(), getProjectName(nativeBinary), nativeBinary.getComponentName());
        VisualStudioProjectConfiguration configuration = createVisualStudioProjectConfiguration(project, nativeBinary, getConfigurationName(nativeBinary.getVariantDimensions()));
        project.addConfiguration(nativeBinary, configuration);
        return configuration;
    }

    private VisualStudioProjectConfiguration createVisualStudioProjectConfiguration(VisualStudioProject project, VisualStudioTargetBinary nativeBinary, String configuration) {
        return getInstantiator().newInstance(VisualStudioProjectConfiguration.class, project, configuration, nativeBinary);
    }

    private DefaultVisualStudioProject getOrCreateProject(String projectPath, String vsProjectName, String componentName) {
        DefaultVisualStudioProject vsProject = findByName(vsProjectName);
        if (vsProject == null) {
            vsProject = getInstantiator().newInstance(DefaultVisualStudioProject.class, vsProjectName, projectPath, componentName, fileResolver, getInstantiator());
            add(vsProject);
        }
        return vsProject;
    }
}
