/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.WrapUtil;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyDescriptorFactory extends AbstractDependencyDescriptorFactoryInternal {
    public final static ProjectDependencyDescriptorStrategy IVY_FILE_DESCRIPTOR_STRATEGY =
            new ProjectDependencyDescriptorStrategy() {
                public ModuleRevisionId createModuleRevisionId(ProjectDependency dependency) {
                    Module module = ((ProjectInternal) dependency.getDependencyProject()).getModule();
                    return IvyUtil.createModuleRevisionId(module);
                }
                public boolean isChanging() {
                    return false;
                }
            };

    public final static ProjectDependencyDescriptorStrategy RESOLVE_DESCRIPTOR_STRATEGY =
            new ProjectDependencyDescriptorStrategy() {
                public ModuleRevisionId createModuleRevisionId(ProjectDependency dependency) {
                    Module module = ((ProjectInternal) dependency.getDependencyProject()).getModule();
                    return IvyUtil.createModuleRevisionId(module, WrapUtil.toMap(DependencyDescriptorFactory.PROJECT_PATH_KEY,
                            dependency.getDependencyProject().getPath()));
                }
                public boolean isChanging() {
                    return true;
                }
            };

    private ProjectDependencyDescriptorStrategy projectDependencyDescriptorStrategy;

    public ProjectDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter, ProjectDependencyDescriptorStrategy projectDependencyDescriptorStrategy) {
        super(excludeRuleConverter);
        this.projectDependencyDescriptorStrategy = projectDependencyDescriptorStrategy;
    }

    public DependencyDescriptor createDependencyDescriptor(ModuleDependency dependency, String configuration, ModuleDescriptor parent,
                                                           ModuleRevisionId moduleRevisionId) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                moduleRevisionId, false, projectDependencyDescriptorStrategy.isChanging(), dependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, dependency, dependencyDescriptor);
        return dependencyDescriptor;
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ProjectDependency;
    }

    public ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        return projectDependencyDescriptorStrategy.createModuleRevisionId((ProjectDependency) dependency);
    }
}
