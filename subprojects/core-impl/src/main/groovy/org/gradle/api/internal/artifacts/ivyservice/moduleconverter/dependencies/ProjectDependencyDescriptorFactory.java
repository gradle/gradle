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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyDescriptorFactory extends AbstractDependencyDescriptorFactoryInternal {
    public ProjectDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    public EnhancedDependencyDescriptor createDependencyDescriptor(ModuleDependency dependency, String configuration, ModuleDescriptor parent,
                                                           ModuleRevisionId moduleRevisionId) {
        ProjectDependency projectDependency = (ProjectDependency) dependency;
        ProjectDependencyDescriptor dependencyDescriptor = new ProjectDependencyDescriptor(projectDependency, parent, moduleRevisionId, false, false, dependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, dependency, dependencyDescriptor);
        return dependencyDescriptor;
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ProjectDependency;
    }

    public ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        ProjectDependency projectDependency = (ProjectDependency) dependency;
        Module module = ((ProjectInternal) projectDependency.getDependencyProject()).getModule();
        return IvyUtil.createModuleRevisionId(module);
    }
}
