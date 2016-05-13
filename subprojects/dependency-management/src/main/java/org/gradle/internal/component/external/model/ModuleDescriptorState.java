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

package org.gradle.internal.component.external.model;

import com.google.common.collect.Lists;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.model.DefaultDependencyMetaData;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ModuleDescriptorState {
    public final ModuleDescriptor ivyDescriptor;

    public ModuleDescriptorState(ModuleDescriptor ivyDescriptor) {
        this.ivyDescriptor = ivyDescriptor;
    }

    public ModuleComponentIdentifier getComponentIdentifier() {
        return DefaultModuleComponentIdentifier.newId(ivyDescriptor.getModuleRevisionId());
    }

    public Map<NamespaceId, String> getExtraInfo() {
        return ivyDescriptor.getExtraInfo();
    }

    public String getBranch() {
        return ivyDescriptor.getModuleRevisionId().getBranch();
    }

    public boolean isDefault() {
        return ivyDescriptor.isDefault();
    }

    public String getStatus() {
        return ivyDescriptor.getStatus();
    }

    public List<String> getConfigurationsNames() {
        return Arrays.asList(ivyDescriptor.getConfigurationsNames());
    }

    public Configuration getConfiguration(String name) {
        return new Configuration(ivyDescriptor.getConfiguration(name));
    }

    public List<Artifact> getArtifacts() {
        List<Artifact> artifacts = Lists.newArrayList();
        for (org.apache.ivy.core.module.descriptor.Artifact ivyArtifact : ivyDescriptor.getAllArtifacts()) {
            IvyArtifactName artifactName = DefaultIvyArtifactName.forIvyArtifact(ivyArtifact);
            List<String> configurations = Lists.newArrayList(ivyArtifact.getConfigurations());
            Artifact artifact = new Artifact(artifactName, configurations);
            artifacts.add(artifact);
        }
        return artifacts;
    }

    public List<DependencyMetaData> getDependencies() {
        List<DependencyMetaData> dependencies = new ArrayList<DependencyMetaData>();
        for (final DependencyDescriptor dependencyDescriptor : ivyDescriptor.getDependencies()) {
            dependencies.add(new DefaultDependencyMetaData(dependencyDescriptor));
        }
        return dependencies;
    }

    public List<ExcludeRule> getExcludeRules() {
        return Lists.newArrayList(ivyDescriptor.getAllExcludeRules());
    }

    public class Configuration {
        public final String name;
        public final boolean transitive;
        public final boolean visiible;
        public final List<String> extendsFrom;

        public Configuration(org.apache.ivy.core.module.descriptor.Configuration configuration) {
            this.name = configuration.getName();
            this.transitive = configuration.isTransitive();
            this.visiible = configuration.getVisibility() == org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC;
            this.extendsFrom = Lists.newArrayList(configuration.getExtends());
        }

        public Configuration(String name, boolean transitive, boolean visiible, List<String> extendsFrom) {
            this.name = name;
            this.transitive = transitive;
            this.visiible = visiible;
            this.extendsFrom = extendsFrom;
        }
    }

    public class Artifact {
        public final IvyArtifactName artifactName;
        public final List<String> configurations;

        public Artifact(IvyArtifactName artifactName, List<String> configurations) {
            this.artifactName = artifactName;
            this.configurations = configurations;
        }
    }
}
