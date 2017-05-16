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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
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

    public VisualStudioProjectConfiguration getProjectConfiguration(NativeBinarySpec nativeBinary) {
        String projectName = projectName(nativeBinary);
        return getByName(projectName).getConfiguration(nativeBinary);
    }

    public VisualStudioProjectConfiguration addProjectConfiguration(NativeBinarySpec nativeBinary) {
        VisualStudioProjectMapper.ProjectConfigurationNames names = projectMapper.mapToConfiguration(nativeBinary);
        DefaultVisualStudioProject project = getOrCreateProject(nativeBinary.getComponent(), names.project);
        VisualStudioProjectConfiguration configuration = createVisualStudioProjectConfiguration(nativeBinary, project, names.configuration, names.platform);
        project.addConfiguration(nativeBinary, configuration);
        return configuration;
    }

    private VisualStudioProjectConfiguration createVisualStudioProjectConfiguration(NativeBinarySpec nativeBinary, DefaultVisualStudioProject project, String configuration, String platform) {
        Class<? extends VisualStudioProjectConfiguration> type =
                (nativeBinary instanceof NativeExecutableBinarySpec  || nativeBinary instanceof NativeTestSuiteBinarySpec)? ExecutableVisualStudioProjectConfiguration.class : VisualStudioProjectConfiguration.class;
        return getInstantiator().newInstance(type, project, configuration, platform, nativeBinary);
    }

    private DefaultVisualStudioProject getOrCreateProject(NativeComponentSpec nativeComponent, String vsProjectName) {
        DefaultVisualStudioProject vsProject = findByName(vsProjectName);
        if (vsProject == null) {
            vsProject = getInstantiator().newInstance(DefaultVisualStudioProject.class, new DefaultComponentSpecIdentifier(projectIdentifier.getPath(), vsProjectName), nativeComponent, fileResolver, getInstantiator());
            add(vsProject);
        }
        return vsProject;
    }

    private String projectName(NativeBinarySpec nativeBinary) {
        return projectMapper.mapToConfiguration(nativeBinary).project;
    }
}
