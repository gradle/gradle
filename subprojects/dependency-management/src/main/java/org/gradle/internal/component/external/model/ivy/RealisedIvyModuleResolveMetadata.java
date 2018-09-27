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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link AbstractRealisedModuleComponentResolveMetadata Realised version} of a {@link IvyModuleResolveMetadata}.
 *
 * @see DefaultIvyModuleResolveMetadata
 */
public class RealisedIvyModuleResolveMetadata extends AbstractRealisedModuleComponentResolveMetadata implements IvyModuleResolveMetadata {

    public static RealisedIvyModuleResolveMetadata transform(DefaultIvyModuleResolveMetadata metadata) {
        VariantMetadataRules variantMetadataRules = metadata.getVariantMetadataRules();

        ImmutableList<ImmutableRealisedVariantImpl> variants = LazyToRealisedModuleComponentResolveMetadataHelper.realiseVariants(metadata, variantMetadataRules, metadata.getVariants());

        Map<String, ConfigurationMetadata> configurations = realiseConfigurations(metadata, variantMetadataRules);

        return new RealisedIvyModuleResolveMetadata(metadata, variants, configurations);
    }

    private static Map<String, ConfigurationMetadata> realiseConfigurations(DefaultIvyModuleResolveMetadata metadata, VariantMetadataRules variantMetadataRules) {
        Map<Artifact, ModuleComponentArtifactMetadata> artifacts = new IdentityHashMap<Artifact, ModuleComponentArtifactMetadata>();
        IvyConfigurationHelper configurationHelper = new IvyConfigurationHelper(metadata.getArtifactDefinitions(), artifacts, metadata.getExcludes(), metadata.getDependencies(), metadata.getId());

        Map<String, ConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(metadata.getConfigurationNames().size());
        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();
        for (String configurationName: metadata.getConfigurationNames()) {
            Configuration configuration = configurationDefinitions.get(configurationName);
            ImmutableSet<String> hierarchy = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration, configurationDefinitions);

            NameOnlyVariantResolveMetadata variant = new NameOnlyVariantResolveMetadata(configurationName);
            ImmutableAttributes variantAttributes = variantMetadataRules.applyVariantAttributeRules(variant, metadata.getAttributes());

            CapabilitiesMetadata capabilitiesMetadata = variantMetadataRules.applyCapabilitiesRules(variant, ImmutableCapabilities.EMPTY);


            configurations.put(configurationName, createConfiguration(configurationHelper, variantMetadataRules, metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(), hierarchy,
                configurationHelper.filterArtifacts(configurationName, hierarchy), configurationHelper.filterExcludes(hierarchy), variantAttributes, ImmutableCapabilities.of(capabilitiesMetadata.getCapabilities())));
        }
        return configurations;
    }

    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final ImmutableList<IvyDependencyDescriptor> dependencies;
    private final ImmutableList<Artifact> artifactDefinitions;
    private final ImmutableList<Exclude> excludes;
    private final ImmutableMap<NamespaceId, String> extraAttributes;
    private final DefaultIvyModuleResolveMetadata metadata;
    private final String branch;

    private RealisedIvyModuleResolveMetadata(RealisedIvyModuleResolveMetadata metadata, List<IvyDependencyDescriptor> dependencies, Map<String, ConfigurationMetadata> transformedConfigurations) {
        super(metadata, metadata.getVariants(), transformedConfigurations);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.branch = metadata.getBranch();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.excludes = metadata.getExcludes();
        this.extraAttributes = metadata.getExtraAttributes();
        this.metadata = metadata.metadata;
    }

    private RealisedIvyModuleResolveMetadata(RealisedIvyModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifactDefinitions = metadata.artifactDefinitions;
        this.dependencies = metadata.dependencies;
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;
        this.metadata = metadata.metadata.withSource(source);
    }

    RealisedIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata,
                                     ImmutableList<? extends ComponentVariant> variants,
                                     Map<String, ConfigurationMetadata> configurations) {
        super(metadata, variants, configurations);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.branch = metadata.getBranch();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.dependencies = metadata.getDependencies();
        this.excludes = metadata.getExcludes();
        this.extraAttributes = metadata.getExtraAttributes();
        this.metadata = metadata;
    }

    private static RealisedConfigurationMetadata createConfiguration(IvyConfigurationHelper configurationHelper, VariantMetadataRules variantMetadataRules, ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableSet<String> hierarchy, ImmutableList<ModuleComponentArtifactMetadata> artifacts, ImmutableList<ExcludeMetadata> excludes, ImmutableAttributes componentLevelAttributes, ImmutableCapabilities capabilities) {
        RealisedConfigurationMetadata configuration = new RealisedConfigurationMetadata(componentId, name, transitive, visible, hierarchy, artifacts, excludes, componentLevelAttributes, capabilities);
        ImmutableList<ModuleDependencyMetadata> dependencyMetadata = configurationHelper.filterDependencies(configuration);
        dependencyMetadata = ImmutableList.copyOf(variantMetadataRules.applyDependencyMetadataRules(new NameOnlyVariantResolveMetadata(name), dependencyMetadata));
        configuration.setDependencies(dependencyMetadata);
        return configuration;
    }

    @Override
    public MutableIvyModuleResolveMetadata asMutable() {
        return metadata.asMutable();
    }

    @Override
    public IvyModuleResolveMetadata withSource(ModuleSource source) {
        return new RealisedIvyModuleResolveMetadata(this, source);
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
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
    public IvyModuleResolveMetadata withDynamicConstraintVersions() {
        ImmutableList<IvyDependencyDescriptor> descriptors = getDependencies();
        if (descriptors.isEmpty()) {
            return this;
        }
        Map<IvyDependencyDescriptor, IvyDependencyDescriptor> transformedDescriptors = Maps.newHashMapWithExpectedSize(descriptors.size());
        for (IvyDependencyDescriptor dependency : descriptors) {
            ModuleComponentSelector selector = dependency.getSelector();
            String dynamicConstraintVersion = dependency.getDynamicConstraintVersion();
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), dynamicConstraintVersion);
            transformedDescriptors.put(dependency, dependency.withRequested(newSelector));
        }
        return this.withDependencies(transformedDescriptors);
    }

    @Override
    public ImmutableList<IvyDependencyDescriptor> getDependencies() {
        return dependencies;
    }

    private IvyModuleResolveMetadata withDependencies(Map<IvyDependencyDescriptor, IvyDependencyDescriptor> transformed) {
        ImmutableList<IvyDependencyDescriptor> transformedDescriptors = ImmutableList.copyOf(transformed.values());
        Set<String> configurationNames = getConfigurationNames();
        Map<String, ConfigurationMetadata> transformedConfigurations = Maps.newHashMapWithExpectedSize(configurationNames.size());
        for (String name : configurationNames) {
            RealisedConfigurationMetadata configuration = (RealisedConfigurationMetadata) getConfiguration(name);
            List<? extends DependencyMetadata> dependencies = configuration.getDependencies();
            ImmutableList.Builder<ModuleDependencyMetadata> transformedConfigurationDependencies = ImmutableList.builder();
            for (DependencyMetadata dependency : dependencies) {
                if (dependency instanceof ConfigurationBoundExternalDependencyMetadata) {
                    transformedConfigurationDependencies.add(((ConfigurationBoundExternalDependencyMetadata) dependency).withDescriptor(transformed.get(((ConfigurationBoundExternalDependencyMetadata) dependency).getDependencyDescriptor())));
                } else {
                    transformedConfigurationDependencies.add((ModuleDependencyMetadata) dependency);
                }
            }
            transformedConfigurations.put(name, configuration.withDependencies(transformedConfigurationDependencies.build()));
        }
        return new RealisedIvyModuleResolveMetadata(this, transformedDescriptors, transformedConfigurations);
    }

}
