/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultIvyModuleResolveMetadata extends AbstractModuleComponentResolveMetadata<IvyConfigurationMetadata> implements IvyModuleResolveMetadata {
    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final ImmutableList<Artifact> artifacts;
    private final ImmutableList<Exclude> excludes;
    private final ImmutableMap<NamespaceId, String> extraAttributes;
    private final ImmutableList<? extends ComponentVariant> variants;
    private final ImmutableList<? extends ConfigurationMetadata> graphVariants;
    private final String branch;

    DefaultIvyModuleResolveMetadata(DefaultMutableIvyModuleResolveMetadata metadata) {
        super(metadata);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.branch = metadata.getBranch();
        this.artifacts = metadata.getArtifactDefinitions();
        this.excludes = metadata.getExcludes();
        this.extraAttributes = metadata.getExtraAttributes();
        this.variants = metadata.getVariants();
        this.graphVariants = metadata.getVariantsForGraphTraversal();
    }

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifacts = metadata.artifacts;
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;
        this.variants = metadata.variants;
        this.graphVariants = metadata.graphVariants;
    }

    @Override
    public DefaultIvyModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultIvyModuleResolveMetadata(this, source);
    }

    @Override
    public MutableIvyModuleResolveMetadata asMutable() {
        return new DefaultMutableIvyModuleResolveMetadata(this);
    }

    @Override
    public ImmutableMap<String, Configuration> getConfigurationDefinitions() {
        return configurationDefinitions;
    }

    @Override
    public ImmutableList<Artifact> getArtifactDefinitions() {
        return artifacts;
    }

    @Override
    public ImmutableList<Exclude> getExcludes() {
        return excludes;
    }

    public String getBranch() {
        return branch;
    }

    public ImmutableMap<NamespaceId, String> getExtraAttributes() {
        return extraAttributes;
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        return variants;
    }

    @Override
    public ImmutableList<? extends ConfigurationMetadata> getVariantsForGraphTraversal() {
        return graphVariants;
    }

    @Override
    public IvyModuleResolveMetadata withDynamicConstraintVersions() {
        List<ModuleDependencyMetadata> transformed = CollectionUtils.collect(getDependencies(), new Transformer<ModuleDependencyMetadata, ModuleDependencyMetadata>() {
            @Override
            public ModuleDependencyMetadata transform(ModuleDependencyMetadata dependency) {
                if (dependency instanceof IvyDependencyMetadata) {
                    String dynamicConstraintVersion = ((IvyDependencyMetadata) dependency).getDynamicConstraintVersion();
                    return dependency.withRequestedVersion(new DefaultMutableVersionConstraint(dynamicConstraintVersion));
                }

                return dependency;
            }
        });
        MutableIvyModuleResolveMetadata mutableMetadata = asMutable();
        mutableMetadata.setDependencies(transformed);
        return mutableMetadata.asImmutable();
    }
}
