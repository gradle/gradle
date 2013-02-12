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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.ReflectionUtil;

public class DefaultProjectModuleRegistry implements ProjectModuleRegistry {
    private final ModuleDescriptorConverter moduleDescriptorConverter;

    public DefaultProjectModuleRegistry(ModuleDescriptorConverter moduleDescriptorConverter) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
    }

    public ModuleDescriptor findProject(ProjectDependencyDescriptor descriptor) {
        ProjectInternal project = descriptor.getTargetProject();
        Module projectModule = project.getModule();
        ModuleDescriptor projectDescriptor = moduleDescriptorConverter.convert(project.getConfigurations(), projectModule);

        for (DependencyArtifactDescriptor artifactDescriptor : descriptor.getAllDependencyArtifacts()) {
            for (Artifact artifact : projectDescriptor.getAllArtifacts()) {
                if (artifact.getName().equals(artifactDescriptor.getName()) && artifact.getExt().equals(
                        artifactDescriptor.getExt())) {
                    String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE);
                    ReflectionUtil.invoke(artifactDescriptor, "setExtraAttribute",
                            new Object[]{DefaultIvyDependencyPublisher.FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE, path});
                }
            }
        }

        return projectDescriptor;
    }
}
