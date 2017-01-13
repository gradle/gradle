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

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadataWrapper;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;

public class ExternalModuleIvyDependencyDescriptorFactory extends AbstractIvyDependencyDescriptorFactory {
    public ExternalModuleIvyDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    public DslOriginDependencyMetadata createDependencyDescriptor(String clientConfiguration, AttributeContainer clientAttributes, ModuleDependency dependency) {
        ExternalModuleDependency externalModuleDependency = (ExternalModuleDependency) dependency;
        boolean force = externalModuleDependency.isForce();
        boolean changing = externalModuleDependency.isChanging();
        boolean transitive = externalModuleDependency.isTransitive();

        ModuleVersionSelector requested = new DefaultModuleVersionSelector(nullToEmpty(dependency.getGroup()), nullToEmpty(dependency.getName()), nullToEmpty(dependency.getVersion()));
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(requested);

        LocalComponentDependencyMetadata dependencyMetaData = new LocalComponentDependencyMetadata(
                selector, requested, clientConfiguration, clientAttributes, dependency.getTargetConfiguration(),
                convertArtifacts(dependency.getArtifacts()),
                convertExcludeRules(clientConfiguration, dependency.getExcludeRules()),
                force, changing, transitive);
        return new DslOriginDependencyMetadataWrapper(dependencyMetaData, dependency);
    }

    private String nullToEmpty(String input) {
        return input == null ? "" : input;
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ExternalModuleDependency;
    }
}
