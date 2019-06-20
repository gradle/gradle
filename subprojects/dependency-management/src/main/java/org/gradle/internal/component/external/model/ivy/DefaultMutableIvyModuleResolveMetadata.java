/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.model.ivy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.Exclude;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultMutableIvyModuleResolveMetadata extends AbstractMutableModuleComponentResolveMetadata implements MutableIvyModuleResolveMetadata {
    private final ImmutableList<Artifact> artifactDefinitions;
    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final ImmutableList<IvyDependencyDescriptor> dependencies;

    private ImmutableList<Exclude> excludes;
    private ImmutableMap<NamespaceId, String> extraAttributes;
    private String branch;

    public DefaultMutableIvyModuleResolveMetadata(ImmutableAttributesFactory attributesFactory,
                                                  ModuleVersionIdentifier id,
                                                  ModuleComponentIdentifier componentIdentifier,
                                                  List<IvyDependencyDescriptor> dependencies,
                                                  Collection<Configuration> configurationDefinitions,
                                                  Collection<? extends Artifact> artifactDefinitions,
                                                  Collection<? extends Exclude> excludes,
                                                  AttributesSchemaInternal schema) {
        super(attributesFactory, id, componentIdentifier, schema);
        this.configurationDefinitions = toMap(configurationDefinitions);
        this.artifactDefinitions = ImmutableList.copyOf(artifactDefinitions);
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.excludes = ImmutableList.of();
        this.extraAttributes = ImmutableMap.of();
        this.excludes = ImmutableList.copyOf(excludes);
    }

    DefaultMutableIvyModuleResolveMetadata(IvyModuleResolveMetadata metadata) {
        super(metadata);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.dependencies = metadata.getDependencies();
        this.excludes = metadata.getExcludes();
        this.branch = metadata.getBranch();
        this.extraAttributes = metadata.getExtraAttributes();
    }

    private static ImmutableMap<String, Configuration> toMap(Collection<Configuration> configurations) {
        ImmutableMap.Builder<String, Configuration> builder = ImmutableMap.builder();
        for (Configuration configuration : configurations) {
            builder.put(configuration.getName(), configuration);
        }
        return builder.build();
    }

    @Override
    public ImmutableMap<String, Configuration> getConfigurationDefinitions() {
        return configurationDefinitions;
    }

    @Override
    public ImmutableList<Artifact> getArtifactDefinitions() {
        return artifactDefinitions;
    }

    @Override
    public ImmutableList<Exclude> getExcludes() {
        return excludes;
    }

    @Override
    public ImmutableMap<NamespaceId, String> getExtraAttributes() {
        return extraAttributes;
    }

    @Override
    public void setExtraAttributes(Map<NamespaceId, String> extraAttributes) {
        this.extraAttributes = ImmutableMap.copyOf(extraAttributes);
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public IvyModuleResolveMetadata asImmutable() {
        return new DefaultIvyModuleResolveMetadata(this);
    }

    @Override
    public ImmutableList<IvyDependencyDescriptor> getDependencies() {
        return dependencies;
    }
}
