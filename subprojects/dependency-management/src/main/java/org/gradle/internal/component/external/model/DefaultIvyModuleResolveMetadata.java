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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultIvyModuleResolveMetadata extends AbstractModuleComponentResolveMetadata implements IvyModuleResolveMetadata {
    private static final PreferJavaRuntimeVariant SCHEMA_DEFAULT_JAVA_VARIANTS = PreferJavaRuntimeVariant.schema();
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

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifactDefinitions = metadata.artifactDefinitions;
        this.dependencies = metadata.dependencies;
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;

        copyCachedState(metadata);
    }

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, List<IvyDependencyDescriptor> dependencies) {
        super(metadata, metadata.getSource());
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifactDefinitions = metadata.artifactDefinitions;
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;

        // Cached state is not copied, since dependency inputs are different.
    }

    @Override
    protected DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy, VariantMetadataRules componentMetadataRules) {
        ImmutableList<ModuleComponentArtifactMetadata> artifacts = filterArtifacts(name, hierarchy);
        ImmutableList<ExcludeMetadata> excludesForConfiguration = filterExcludes(hierarchy);

        DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(componentId, name, transitive, visible, hierarchy, ImmutableList.copyOf(artifacts), componentMetadataRules, excludesForConfiguration);
        configuration.setDependencies(filterDependencies(configuration));
        return configuration;
    }

    private ImmutableList<ModuleComponentArtifactMetadata> filterArtifacts(String name, ImmutableList<String> hierarchy) {
        Set<ModuleComponentArtifactMetadata> artifacts = new LinkedHashSet<ModuleComponentArtifactMetadata>();
        collectArtifactsFor(name, artifacts);
        for (String parent : hierarchy) {
            collectArtifactsFor(parent, artifacts);
        }
        return ImmutableList.copyOf(artifacts);
    }

    private void collectArtifactsFor(String name, Collection<ModuleComponentArtifactMetadata> dest) {
        if (artifacts == null) {
            artifacts = new IdentityHashMap<Artifact, ModuleComponentArtifactMetadata>();
        }
        for (Artifact artifact : artifactDefinitions) {
            if (artifact.getConfigurations().contains(name)) {
                ModuleComponentArtifactMetadata artifactMetadata = artifacts.get(artifact);
                if (artifactMetadata == null) {
                    artifactMetadata = new DefaultModuleComponentArtifactMetadata(getComponentId(), artifact.getArtifactName());
                    artifacts.put(artifact, artifactMetadata);
                }
                dest.add(artifactMetadata);
            }
        }
    }

    private ImmutableList<ExcludeMetadata> filterExcludes(ImmutableList<String> hierarchy) {
        ImmutableList.Builder<ExcludeMetadata> filtered = ImmutableList.builder();
        for (Exclude exclude : excludes) {
            for (String config : exclude.getConfigurations()) {
                if (hierarchy.contains(config)) {
                    filtered.add(exclude);
                    break;
                }
            }
        }
        return filtered.build();
    }

    private ImmutableList<ModuleDependencyMetadata> filterDependencies(DefaultConfigurationMetadata config) {
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = ImmutableList.builder();
        for (IvyDependencyDescriptor dependency : dependencies) {
            if (include(dependency, config.getName(), config.getHierarchy())) {
                filteredDependencies.add(contextualize(config, getComponentId(), dependency));
            }
        }
        return filteredDependencies.build();
    }

    private ModuleDependencyMetadata contextualize(ConfigurationMetadata config, ModuleComponentIdentifier componentId, IvyDependencyDescriptor incoming) {
        return new ConfigurationDependencyMetadataWrapper(config, componentId, incoming);
    }

    private boolean include(IvyDependencyDescriptor dependency, String configName, Collection<String> hierarchy) {
        Set<String> dependencyConfigurations = dependency.getConfMappings().keySet();
        for (String moduleConfiguration : dependencyConfigurations) {
            if (moduleConfiguration.equals("%") || hierarchy.contains(moduleConfiguration)) {
                return true;
            }
            if (moduleConfiguration.equals("*")) {
                boolean include = true;
                for (String conf2 : dependencyConfigurations) {
                    if (conf2.startsWith("!") && conf2.substring(1).equals(configName)) {
                        include = false;
                        break;
                    }
                }
                if (include) {
                    return true;
                }
            }
        }
        return false;
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
        return artifactDefinitions;
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
    public IvyModuleResolveMetadata withDynamicConstraintVersions() {
        List<IvyDependencyDescriptor> transformed = CollectionUtils.collect(getDependencies(), new Transformer<IvyDependencyDescriptor, IvyDependencyDescriptor>() {
            @Override
            public IvyDependencyDescriptor transform(IvyDependencyDescriptor dependency) {
                ModuleComponentSelector selector = dependency.getSelector();
                    String dynamicConstraintVersion = dependency.getDynamicConstraintVersion();
                    ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getGroup(), selector.getModule(), dynamicConstraintVersion);
                    return dependency.withRequested(newSelector);
            }
        });
        return this.withDependencies(transformed);
    }

    private IvyModuleResolveMetadata withDependencies(List<IvyDependencyDescriptor> transformed) {
        return new DefaultIvyModuleResolveMetadata(this, transformed);
    }

    @Nullable
    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return SCHEMA_DEFAULT_JAVA_VARIANTS;
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
