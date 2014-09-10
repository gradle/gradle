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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetaData;
import org.gradle.internal.component.local.model.ProjectDependencyMetaData;
import org.gradle.api.internal.project.ProjectInternal;

public class ProjectIvyDependencyDescriptorFactory extends AbstractIvyDependencyDescriptorFactory {
    public ProjectIvyDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    public ProjectDependencyMetaData createDependencyDescriptor(String configuration, ModuleDependency dependency, ModuleDescriptor parent) {
        ProjectDependencyInternal projectDependency = (ProjectDependencyInternal) dependency;
        projectDependency.beforeResolved();
        ModuleRevisionId moduleRevisionId = createModuleRevisionId(dependency);
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent, moduleRevisionId, false, false, dependency.isTransitive());
        addExcludesArtifactsAndDependencies(configuration, dependency, dependencyDescriptor);
        return new DefaultProjectDependencyMetaData(dependencyDescriptor, projectDependency);
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ProjectDependency;
    }

    private ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        ProjectDependency projectDependency = (ProjectDependency) dependency;
        Module module = ((ProjectInternal) projectDependency.getDependencyProject()).getModule();
        return IvyUtil.createModuleRevisionId(module);
    }
}
