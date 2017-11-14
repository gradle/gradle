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

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadataWrapper;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.List;

public class ProjectIvyDependencyDescriptorFactory extends AbstractIvyDependencyDescriptorFactory {

    public ProjectIvyDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    public LocalOriginDependencyMetadata createDependencyDescriptor(String clientConfiguration, AttributeContainer clientAttributes, ModuleDependency dependency) {
        ProjectDependencyInternal projectDependency = (ProjectDependencyInternal) dependency;
        projectDependency.beforeResolved();
        ComponentSelector selector = DefaultProjectComponentSelector.newSelector(projectDependency.getDependencyProject());

        List<Exclude> excludes = convertExcludeRules(clientConfiguration, dependency.getExcludeRules());
        LocalComponentDependencyMetadata dependencyMetaData = new LocalComponentDependencyMetadata(
            selector, clientConfiguration,
            clientAttributes,
            projectDependency.getTargetConfiguration(),
            convertArtifacts(dependency.getArtifacts()),
            excludes,
            false, false, dependency.isTransitive());
        return new DslOriginDependencyMetadataWrapper(dependencyMetaData, dependency);
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ProjectDependency;
    }

    private Module getProjectModule(ModuleDependency dependency) {
        ProjectDependency projectDependency = (ProjectDependency) dependency;
        return ((ProjectInternal) projectDependency.getDependencyProject()).getModule();
    }
}
