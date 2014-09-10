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
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.internal.component.local.model.DefaultDslOriginDependencyMetaData;
import org.gradle.internal.component.local.model.DslOriginDependencyMetaData;

public class ExternalModuleIvyDependencyDescriptorFactory extends AbstractIvyDependencyDescriptorFactory {
    public ExternalModuleIvyDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    private ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        return IvyUtil.createModuleRevisionId(dependency);
    }

    public DslOriginDependencyMetaData createDependencyDescriptor(String configuration, ModuleDependency dependency, ModuleDescriptor parent) {
        ModuleRevisionId moduleRevisionId = createModuleRevisionId(dependency);
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(
                parent,
                moduleRevisionId,
                getExternalModuleDependency(dependency).isForce(),
                getExternalModuleDependency(dependency).isChanging(),
                getExternalModuleDependency(dependency).isTransitive());
        addExcludesArtifactsAndDependencies(configuration, getExternalModuleDependency(dependency), dependencyDescriptor);
        return new DefaultDslOriginDependencyMetaData(dependencyDescriptor, dependency);
    }

    private ExternalModuleDependency getExternalModuleDependency(ModuleDependency dependency) {
        return (ExternalModuleDependency) dependency;
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ExternalModuleDependency;
    }
}
