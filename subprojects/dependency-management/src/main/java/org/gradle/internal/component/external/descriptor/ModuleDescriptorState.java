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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleDescriptorState {
    private final ModuleComponentIdentifier componentIdentifier;
    protected final Map<String, Configuration> configurations;
    protected final List<Exclude> excludes;
    protected final List<Dependency> dependencies;
    private final List<Artifact> artifacts = Lists.newArrayList();
    private final String status;
    private final boolean generated;
    private final Map<NamespaceId, String> extraInfo;
    protected String description;
    protected String branch;
    protected Date publicationDate;

    public ModuleDescriptorState(ModuleComponentIdentifier componentIdentifier, String status, boolean generated) {
        this.componentIdentifier = componentIdentifier;
        branch = null;
        description = null;
        publicationDate = new Date();
        this.status = status;
        this.generated = generated;
        extraInfo = Maps.newHashMap();
        configurations = Maps.newHashMap();
        excludes = Lists.newArrayList();
        dependencies = Lists.newArrayList();
    }

    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

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

    public Set<String> getConfigurationsNames() {
        return configurations.keySet();
    }

    public Configuration getConfiguration(String name) {
        return configurations.get(name);
    }

    public Collection<Configuration> getConfigurations() {
        return configurations.values();
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void addArtifact(IvyArtifactName newArtifact, Set<String> configurations) {
        if (configurations.isEmpty()) {
            throw new IllegalArgumentException("Artifact should be attached to at least one configuration.");
        }
        Set<String> configurationNames = getConfigurationsNames();
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

    public List<Exclude> getExcludes() {
        return excludes;
    }
}
