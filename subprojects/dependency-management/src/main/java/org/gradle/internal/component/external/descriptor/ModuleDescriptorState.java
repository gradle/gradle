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
import com.google.common.collect.Maps;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleDescriptorState {
    // TODO:DAZ Remove this
    public final ModuleDescriptor ivyDescriptor;

    private final ModuleComponentIdentifier componentIdentifier;
    private final Map<String, Configuration> configurations;
    private final List<ExcludeRule> excludeRules;
    private final List<Dependency> dependencies;
    private final List<Artifact> artifacts = Lists.newArrayList();
    private final String description;
    private final Date publicationDate;
    private final String status;
    private final String branch;
    private final boolean generated;
    private final Map<NamespaceId, String> extraInfo;

    public ModuleDescriptorState(ModuleDescriptor ivyDescriptor) {
        // Force attribute is ignored in published modules: we only consider force attribute on direct dependencies declared in Gradle
        this(ivyDescriptor, false);
    }

    public ModuleDescriptorState(ModuleDescriptor ivyDescriptor, final boolean allowForcedDependencies) {
        this.ivyDescriptor = ivyDescriptor;

        ModuleRevisionId moduleRevisionId = ivyDescriptor.getModuleRevisionId();
        componentIdentifier = DefaultModuleComponentIdentifier.newId(moduleRevisionId);
        branch = moduleRevisionId.getBranch();
        description = ivyDescriptor.getDescription();
        publicationDate = ivyDescriptor.getPublicationDate();
        status = ivyDescriptor.getStatus();
        generated = ivyDescriptor.isDefault();
        extraInfo = Cast.uncheckedCast(ivyDescriptor.getExtraInfo());

        configurations = Maps.newLinkedHashMap();
        for (org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration : ivyDescriptor.getConfigurations()) {
            Configuration configuration = new Configuration(ivyConfiguration);
            configurations.put(configuration.getName(), configuration);
        }
        excludeRules = Lists.newArrayList(ivyDescriptor.getAllExcludeRules());
        dependencies = CollectionUtils.collect(ivyDescriptor.getDependencies(), new Transformer<Dependency, DependencyDescriptor>() {
            @Override
            public Dependency transform(DependencyDescriptor dependencyDescriptor) {
                boolean force = allowForcedDependencies && dependencyDescriptor.isForce();
                return Dependency.forDependencyDescriptor(dependencyDescriptor, force);
            }
        });
    }

    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    // TODO:DAZ Description and publicationDate only need to be persisted so we can detect changes (for integration tests)
    public String getDescription() {
        return description;
    }
    public Date getPublicationDate() {
        return publicationDate;
    }

    public Map<NamespaceId, String> getExtraInfo() {
        return extraInfo;
    }

    public String getBranch() {
        return branch;
    }

    public boolean isGenerated() {
        return generated;
    }

    public String getStatus() {
        return status;
    }

    // TODO:DAZ Remove all of this collection wrapping
    public List<String> getConfigurationsNames() {
        return Lists.newArrayList(configurations.keySet());
    }

    public Configuration getConfiguration(String name) {
        return configurations.get(name);
    }

    public List<Configuration> getConfigurations() {
        return Lists.newArrayList(configurations.values());
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void addArtifact(IvyArtifactName newArtifact, Set<String> configurations) {
        if (configurations.isEmpty()) {
            throw new IllegalArgumentException("Artifact should be attached to at least one configuration.");
        }
        List<String> configurationNames = getConfigurationsNames();
        for (String configuration : configurations) {
            if (!configurationNames.contains(configuration)) {
                throw new IllegalArgumentException("Cannot add artifact '" + newArtifact
                        + "' to configuration '" + configuration + "' of module " + getComponentIdentifier()
                        + " because this configuration doesn't exist!");
            }
        }
        Artifact artifact = findOrCreate(newArtifact);
        artifact.getConfigurations().addAll(configurations);
    }

    private Artifact findOrCreate(IvyArtifactName artifactName) {
        for (Artifact existingArtifact : artifacts) {
            if (existingArtifact.getArtifactName().equals(artifactName)) {
                return existingArtifact;
            }
        }
        Artifact newArtifact = new Artifact(artifactName);
        artifacts.add(newArtifact);
        return newArtifact;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public List<ExcludeRule> getExcludeRules() {
        return excludeRules;
    }
}
