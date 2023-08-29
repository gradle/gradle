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

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.VersionConstraintInternal;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadataWrapper;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import javax.annotation.Nullable;
import java.util.List;

public class ExternalModuleDependencyMetadataConverter extends AbstractDependencyMetadataConverter {
    public ExternalModuleDependencyMetadataConverter(ExcludeRuleConverter excludeRuleConverter) {
        super(excludeRuleConverter);
    }

    @Override
    public LocalOriginDependencyMetadata createDependencyMetadata(ModuleDependency dependency) {
        ExternalModuleDependency externalModuleDependency = (ExternalModuleDependency) dependency;
        boolean force = externalModuleDependency.isForce();
        boolean changing = externalModuleDependency.isChanging();
        boolean transitive = externalModuleDependency.isTransitive();

        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(
                DefaultModuleIdentifier.newId(nullToEmpty(dependency.getGroup()), nullToEmpty(dependency.getName())),
                ((VersionConstraintInternal) externalModuleDependency.getVersionConstraint()).asImmutable(),
                dependency.getAttributes(),
                dependency.getRequestedCapabilities());

        List<ExcludeMetadata> excludes = convertExcludeRules(dependency.getExcludeRules());
        LocalComponentDependencyMetadata dependencyMetaData = new LocalComponentDependencyMetadata(
                selector,
                dependency.getAttributes(),
                dependency.getTargetConfiguration(),
                convertArtifacts(dependency.getArtifacts()),
                excludes, force, changing, transitive, false, dependency.isEndorsingStrictVersions(), dependency.getReason());
        return new DslOriginDependencyMetadataWrapper(dependencyMetaData, dependency);
    }

    private String nullToEmpty(@Nullable String input) {
        return input == null ? "" : input;
    }

    @Override
    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ExternalModuleDependency;
    }
}
