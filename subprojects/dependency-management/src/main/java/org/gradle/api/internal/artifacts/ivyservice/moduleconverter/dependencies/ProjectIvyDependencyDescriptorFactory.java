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

import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.DslOriginDependencyMetaData;
import org.gradle.internal.component.local.model.DslOriginDependencyMetaDataWrapper;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;

public class ProjectIvyDependencyDescriptorFactory extends AbstractIvyDependencyDescriptorFactory {
    public ProjectIvyDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    public DslOriginDependencyMetaData createDependencyDescriptor(String configuration, ModuleDependency dependency) {
        ProjectDependencyInternal projectDependency = (ProjectDependencyInternal) dependency;
        projectDependency.beforeResolved();
        ((ConfigurationInternal) projectDependency.getProjectConfiguration()).triggerWhenEmptyActionsIfNecessary();
        Module module = getProjectModule(dependency);
        ModuleVersionSelector requested = new DefaultModuleVersionSelector(module.getGroup(), module.getName(), module.getVersion());
        ComponentSelector selector = DefaultProjectComponentSelector.newSelector(projectDependency.getDependencyProject().getPath());

        LocalComponentDependencyMetaData dependencyMetaData = new LocalComponentDependencyMetaData(
                selector, requested, configuration, dependency.getConfiguration(),
                convertArtifacts(dependency.getArtifacts()),
                convertExcludeRules(configuration, dependency.getExcludeRules()),
                false, false, dependency.isTransitive());
        return new DslOriginDependencyMetaDataWrapper(dependencyMetaData, dependency);
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ProjectDependency;
    }

    private Module getProjectModule(ModuleDependency dependency) {
        ProjectDependency projectDependency = (ProjectDependency) dependency;
        return ((ProjectInternal) projectDependency.getDependencyProject()).getModule();
    }
}
