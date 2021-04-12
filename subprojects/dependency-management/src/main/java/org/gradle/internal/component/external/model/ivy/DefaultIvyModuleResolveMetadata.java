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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.util.internal.CollectionUtils;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AbstractLazyModuleComponentResolveMetadata Lazy version} of a {@link IvyModuleResolveMetadata}.
 *
 * @see RealisedIvyModuleResolveMetadata
 */
public class DefaultIvyModuleResolveMetadata extends AbstractLazyModuleComponentResolveMetadata implements IvyModuleResolveMetadata {
    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final ImmutableList<IvyDependencyDescriptor> dependencies;
    private final ImmutableList<Artifact> artifactDefinitions;
    private final ImmutableList<Exclude> excludes;
    private final ImmutableMap<NamespaceId, String> extraAttributes;
    private final String branch;
    // Since a single `Artifact` is shared between configurations, share the metadata type as well.
    private Map<Artifact, ModuleComponentArtifactMetadata> artifacts;

    DefaultIvyModuleResolveMetadata(DefaultMutableIvyModuleResolveMetadata metadata) {
        super(metadata);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.branch = metadata.getBranch();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.dependencies = metadata.getDependencies();
        this.excludes = metadata.getExcludes();
        this.extraAttributes = metadata.getExtraAttributes();
    }

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, ModuleSources sources, VariantDerivationStrategy variantDerivationStrategy) {
        super(metadata, sources, variantDerivationStrategy);
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifactDefinitions = metadata.artifactDefinitions;
        this.dependencies = metadata.dependencies;
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;

        copyCachedState(metadata, metadata.getVariantDerivationStrategy() != variantDerivationStrategy);
    }

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, List<IvyDependencyDescriptor> dependencies) {
        super(metadata, metadata.getSources(), metadata.getVariantDerivationStrategy());
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifactDefinitions = metadata.artifactDefinitions;
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;

        // Cached state is not copied, since dependency inputs are different.
    }

    @Override
    protected DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableSet<String> hierarchy, VariantMetadataRules componentMetadataRules) {
        if (artifacts == null) {
            artifacts = new IdentityHashMap<>();
        }
        IvyConfigurationHelper configurationHelper = new IvyConfigurationHelper(artifactDefinitions, artifacts, excludes, dependencies, componentId);
        ImmutableList<ModuleComponentArtifactMetadata> artifacts = configurationHelper.filterArtifacts(name, hierarchy);
        ImmutableList<ExcludeMetadata> excludesForConfiguration = configurationHelper.filterExcludes(hierarchy);

        DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(componentId, name, transitive, visible, hierarchy, ImmutableList.copyOf(artifacts), componentMetadataRules, excludesForConfiguration, getAttributes().asImmutable(), false, false);
        configuration.setDependencies(configurationHelper.filterDependencies(configuration));
        return configuration;
    }

    @Override
    public MutableIvyModuleResolveMetadata asMutable() {
        return new DefaultMutableIvyModuleResolveMetadata(this);
    }

    @Override
    public DefaultIvyModuleResolveMetadata withSources(ModuleSources sources) {
        return new DefaultIvyModuleResolveMetadata(this, sources, getVariantDerivationStrategy());
    }


    @Override
    public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
        if (getVariantDerivationStrategy() == derivationStrategy) {
            return this;
        }
        return new DefaultIvyModuleResolveMetadata(this, getSources(), derivationStrategy);
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
    public String getBranch() {
        return branch;
    }

    @Override
    public ImmutableMap<NamespaceId, String> getExtraAttributes() {
        return extraAttributes;
    }

    @Override
    public IvyModuleResolveMetadata withDynamicConstraintVersions() {
        List<IvyDependencyDescriptor> transformed = CollectionUtils.collect(getDependencies(), dependency -> {
            ModuleComponentSelector selector = dependency.getSelector();
                String dynamicConstraintVersion = dependency.getDynamicConstraintVersion();
                ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), dynamicConstraintVersion);
                return dependency.withRequested(newSelector);
        });
        return this.withDependencies(transformed);
    }

    private IvyModuleResolveMetadata withDependencies(List<IvyDependencyDescriptor> transformed) {
        return new DefaultIvyModuleResolveMetadata(this, transformed);
    }

    @Override
    public ImmutableList<IvyDependencyDescriptor> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultIvyModuleResolveMetadata that = (DefaultIvyModuleResolveMetadata) o;
        return Objects.equal(dependencies, that.dependencies)
            && Objects.equal(artifactDefinitions, that.artifactDefinitions)
            && Objects.equal(excludes, that.excludes)
            && Objects.equal(extraAttributes, that.extraAttributes)
            && Objects.equal(branch, that.branch)
            && Objects.equal(artifacts, that.artifacts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(),
            dependencies,
            artifactDefinitions,
            excludes,
            extraAttributes,
            branch,
            artifacts);
    }
}
