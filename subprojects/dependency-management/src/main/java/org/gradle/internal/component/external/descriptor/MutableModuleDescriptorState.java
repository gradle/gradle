/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.descriptor;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public class MutableModuleDescriptorState extends ModuleDescriptorState {

    public MutableModuleDescriptorState(ModuleComponentIdentifier componentIdentifier) {
        super(componentIdentifier, "integration", true);
    }

    public MutableModuleDescriptorState(ModuleComponentIdentifier componentIdentifier, String status, boolean generated) {
        super(componentIdentifier, status, generated);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public void setPublicationDate(Date publicationDate) {
        this.publicationDate = publicationDate;
    }

    public void addConfiguration(String name, boolean transitive, boolean visible, Collection<String> extendsFrom) {
        Configuration configuration = new Configuration(name, transitive, visible, extendsFrom);
        configurations.put(name, configuration);
    }

    public void addExclude(Exclude exclude) {
        excludes.add(exclude);
    }

    public Dependency addDependency(ModuleVersionSelector requested) {
        return addDependency(requested, requested.getVersion(), false, false, true);
    }

    public Dependency addDependency(ModuleVersionSelector requested, String dynamicConstraintVersion, boolean force, boolean changing, boolean transitive) {
        Dependency dependency = new Dependency(requested, dynamicConstraintVersion, force, changing, transitive);
        dependencies.add(dependency);
        return dependency;
    }

    public void addDependency(DependencyMetadata dependencyMetadata) {
        Dependency dependency = new Dependency(
            dependencyMetadata.getRequested(),
            dependencyMetadata.getDynamicConstraintVersion(),
            dependencyMetadata.isForce(),
            dependencyMetadata.isChanging(),
            dependencyMetadata.isTransitive());

        // In reality, there will only be 1 module configuration and 1 matching dependency configuration
        List<String> configurations = Lists.newArrayList(dependencyMetadata.getModuleConfigurations());
        for (String moduleConfiguration : configurations) {
            for (String dependencyConfiguration : dependencyMetadata.getDependencyConfigurations(moduleConfiguration, moduleConfiguration)) {
                dependency.addDependencyConfiguration(moduleConfiguration, dependencyConfiguration);
            }
        }

        for (IvyArtifactName artifactName : dependencyMetadata.getArtifacts()) {
            dependency.addArtifact(artifactName, configurations);
        }

        dependencies.add(dependency);
    }
}
