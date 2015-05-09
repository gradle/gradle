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
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DslOriginDependencyMetaData;
import org.gradle.internal.component.local.model.DslOriginDependencyMetaDataWrapper;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;

public class ExternalModuleIvyDependencyDescriptorFactory extends AbstractIvyDependencyDescriptorFactory {
    public ExternalModuleIvyDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    private ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        return IvyUtil.createModuleRevisionId(dependency);
    }

    public DslOriginDependencyMetaData createDependencyDescriptor(String configuration, ModuleDependency dependency, ModuleDescriptor parent) {
        ExternalModuleDependency externalModuleDependency = (ExternalModuleDependency) dependency;
        boolean force = externalModuleDependency.isForce();
        boolean changing = externalModuleDependency.isChanging();
        boolean transitive = externalModuleDependency.isTransitive();

        ModuleRevisionId moduleRevisionId = createModuleRevisionId(dependency);
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent, moduleRevisionId, force, changing, transitive);
        configureDependencyDescriptor(configuration, externalModuleDependency, dependencyDescriptor);

        // TODO:DAZ Only using ModuleRevisionId here to convert nulls to empty strings
        ModuleVersionSelector requested = new DefaultModuleVersionSelector(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(requested);

        LocalComponentDependencyMetaData dependencyMetaData = new LocalComponentDependencyMetaData(
                selector, requested, configuration, dependency.getConfiguration(),
                convertExcludeRules(configuration, dependency.getExcludeRules()),
                force, changing, transitive,
                dependencyDescriptor);
        return new DslOriginDependencyMetaDataWrapper(dependencyMetaData, dependency);
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ExternalModuleDependency;
    }
}
