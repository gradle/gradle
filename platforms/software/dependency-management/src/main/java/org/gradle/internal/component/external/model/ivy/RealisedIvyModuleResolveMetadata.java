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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AdditionalVariant;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ExternalVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        Map<String, ModuleConfigurationMetadata> configurations = realiseConfigurations(metadata, variantMetadataRules);

        if (variants.isEmpty()) {
            addVariantsFromRules(metadata, configurations, variantMetadataRules);
        }

        return new RealisedIvyModuleResolveMetadata(metadata, variants, configurations);
    }

    private static Map<String, ModuleConfigurationMetadata> realiseConfigurations(DefaultIvyModuleResolveMetadata metadata, VariantMetadataRules variantMetadataRules) {
        Map<String, ModuleConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(metadata.getConfigurationNames().size());
        for (String configurationName : metadata.getConfigurationNames()) {
            configurations.put(configurationName, applyRules(metadata, variantMetadataRules, configurationName));
        }
        return configurations;
    }

    private static void addVariantsFromRules(DefaultIvyModuleResolveMetadata componentMetadata, Map<String, ModuleConfigurationMetadata> declaredConfigurations, VariantMetadataRules variantMetadataRules) {
        List<AdditionalVariant> additionalVariants = variantMetadataRules.getAdditionalVariants();
        if (additionalVariants.isEmpty()) {
            return;
        }

        for (AdditionalVariant additionalVariant : additionalVariants) {
            String name = additionalVariant.getName();
            String baseName = additionalVariant.getBase();
            ImmutableAttributes attributes;
            ImmutableCapabilities capabilities;
            List<ModuleDependencyMetadata> dependencies;
            ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;
            ImmutableList<ExcludeMetadata> excludes;

            ModuleConfigurationMetadata baseConf = declaredConfigurations.get(baseName);
            if (baseConf == null) {
                attributes = componentMetadata.getAttributes();
                capabilities = ImmutableCapabilities.EMPTY;
                dependencies = ImmutableList.of();
                artifacts = ImmutableList.of();
                excludes = ImmutableList.of();
            } else {
                attributes = baseConf.getAttributes();
                capabilities = baseConf.getCapabilities();
                dependencies = Cast.uncheckedCast(baseConf.getDependencies());
                artifacts = Cast.uncheckedCast(baseConf.getArtifacts());
                excludes = Cast.uncheckedCast(baseConf.getExcludes());
            }

            if (baseName == null || baseConf != null) {
                declaredConfigurations.put(name, applyRules(componentMetadata.getId(),
                    name, variantMetadataRules, attributes, capabilities,
                    artifacts, excludes, true, true,
                    ImmutableSet.of(), null, dependencies, true, false));
            } else if (!additionalVariant.isLenient()) {
                throw new InvalidUserDataException("Configuration '" + baseName + "' not defined in module " + componentMetadata.getId().getDisplayName());
            }
        }
    }

    private static RealisedConfigurationMetadata applyRules(DefaultIvyModuleResolveMetadata metadata, VariantMetadataRules variantMetadataRules, String configurationName) {
        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();
        Configuration configuration = configurationDefinitions.get(configurationName);
        IvyConfigurationHelper configurationHelper = new IvyConfigurationHelper(metadata.getArtifactDefinitions(), new IdentityHashMap<>(), metadata.getExcludes(), metadata.getDependencies(), metadata.getId());
        ImmutableSet<String> hierarchy = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration, configurationDefinitions);
        ImmutableList<ExcludeMetadata> excludes = configurationHelper.filterExcludes(hierarchy);

        ImmutableList<ModuleComponentArtifactMetadata> artifacts = configurationHelper.filterArtifacts(configurationName, hierarchy);

        return applyRules(metadata.getId(), configurationName, variantMetadataRules, metadata.getAttributes(), ImmutableCapabilities.EMPTY, artifacts, excludes, configuration.isTransitive(), configuration.isVisible(), hierarchy, configurationHelper, null, false, metadata.isExternalVariant());
    }

    private static RealisedConfigurationMetadata applyRules(
        ModuleComponentIdentifier id,
        String configurationName,
        VariantMetadataRules variantMetadataRules,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
        ImmutableList<ExcludeMetadata> excludes,
        boolean transitive,
        boolean visible,
        ImmutableSet<String> hierarchy,
        IvyConfigurationHelper configurationHelper,
        @Nullable List<ModuleDependencyMetadata> dependenciesOverride,
        boolean addedByRule,
        boolean isExternalVariant
    ) {
        NameOnlyVariantResolveMetadata variant = new NameOnlyVariantResolveMetadata(configurationName);
        ImmutableAttributes variantAttributes = variantMetadataRules.applyVariantAttributeRules(variant, attributes);
        ImmutableCapabilities variantCapabilities = variantMetadataRules.applyCapabilitiesRules(variant, capabilities);
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifactsMetadata = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts(variant, artifacts, id);
        return createConfiguration(id, configurationName, transitive, visible, hierarchy,
            artifactsMetadata, excludes, variantAttributes, variantCapabilities, variantMetadataRules, configurationHelper, dependenciesOverride, addedByRule, isExternalVariant);
    }

    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final ImmutableList<IvyDependencyDescriptor> dependencies;
    private final ImmutableList<Artifact> artifactDefinitions;
    private final ImmutableList<Exclude> excludes;
    private final ImmutableMap<NamespaceId, String> extraAttributes;
    private final DefaultIvyModuleResolveMetadata metadata;
    private final String branch;

    private Optional<List<? extends ExternalVariantGraphResolveMetadata>> derivedVariants;

    private RealisedIvyModuleResolveMetadata(RealisedIvyModuleResolveMetadata metadata, List<IvyDependencyDescriptor> dependencies, Map<String, ModuleConfigurationMetadata> transformedConfigurations) {
        super(metadata, metadata.getVariants(), transformedConfigurations);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.branch = metadata.getBranch();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.excludes = metadata.getExcludes();
        this.extraAttributes = metadata.getExtraAttributes();
        this.metadata = metadata.metadata;
    }

    private RealisedIvyModuleResolveMetadata(RealisedIvyModuleResolveMetadata metadata, ModuleSources sources, VariantDerivationStrategy derivationStrategy) {
        super(metadata, sources, derivationStrategy);
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.branch = metadata.branch;
        this.artifactDefinitions = metadata.artifactDefinitions;
        this.dependencies = metadata.dependencies;
        this.excludes = metadata.excludes;
        this.extraAttributes = metadata.extraAttributes;
        this.metadata = metadata.metadata;
    }

    RealisedIvyModuleResolveMetadata(
        DefaultIvyModuleResolveMetadata metadata,
        ImmutableList<? extends ComponentVariant> variants,
        Map<String, ModuleConfigurationMetadata> configurations
    ) {
        super(metadata, variants, configurations);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.branch = metadata.getBranch();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.dependencies = metadata.getDependencies();
        this.excludes = metadata.getExcludes();
        this.extraAttributes = metadata.getExtraAttributes();
        this.metadata = metadata;
    }

    private static RealisedConfigurationMetadata createConfiguration(
        ModuleComponentIdentifier componentId,
        String name,
        boolean transitive,
        boolean visible,
        ImmutableSet<String> hierarchy,
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
        ImmutableList<ExcludeMetadata> excludes,
        ImmutableAttributes componentLevelAttributes,
        ImmutableCapabilities capabilities,
        VariantMetadataRules variantMetadataRules,
        IvyConfigurationHelper configurationHelper,
        List<ModuleDependencyMetadata> dependenciesFromRule,
        boolean addedByRule,
        boolean externalVariant
    ) {
        RealisedConfigurationMetadata configuration = new RealisedConfigurationMetadata(componentId, name, transitive, visible, hierarchy, artifacts, excludes, componentLevelAttributes, capabilities, addedByRule, externalVariant);
        List<ModuleDependencyMetadata> dependencyMetadata;
        if (configurationHelper != null) {
            dependencyMetadata = configurationHelper.filterDependencies(configuration);
        } else {
            dependencyMetadata = dependenciesFromRule;
        }
        configuration.setDependencies(ImmutableList.copyOf(variantMetadataRules.applyDependencyMetadataRules(new NameOnlyVariantResolveMetadata(name), dependencyMetadata)));
        return configuration;
    }

    @Override
    protected Optional<List<? extends ExternalVariantGraphResolveMetadata>> maybeDeriveVariants() {
        if (derivedVariants == null && getConfigurationNames().size() != configurationDefinitions.size()) {
            // if there are more configurations than definitions, configurations have been added by rules and thus they are variants
            derivedVariants = Optional.of(allConfigurationsThatAreVariants());
        } else {
            derivedVariants = Optional.empty();
        }
        return derivedVariants;
    }

    private ImmutableList<? extends ModuleConfigurationMetadata> allConfigurationsThatAreVariants() {
        ImmutableList.Builder<ModuleConfigurationMetadata> builder = new ImmutableList.Builder<>();
        for (String potentialVariantName : getConfigurationNames()) {
            if (!configurationDefinitions.containsKey(potentialVariantName)) {
                builder.add(getConfiguration(potentialVariantName));
            }
        }
        return builder.build();
    }

    @Override
    public MutableIvyModuleResolveMetadata asMutable() {
        return metadata.asMutable();
    }

    @Override
    public RealisedIvyModuleResolveMetadata withSources(ModuleSources sources) {
        return new RealisedIvyModuleResolveMetadata(this, sources, getVariantDerivationStrategy());
    }

    @Override
    public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
        if (getVariantDerivationStrategy() == derivationStrategy) {
            return this;
        }
        return new RealisedIvyModuleResolveMetadata(this, getSources(), derivationStrategy);
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
        Map<String, ModuleConfigurationMetadata> transformedConfigurations = Maps.newHashMapWithExpectedSize(configurationNames.size());
        for (String name : configurationNames) {
            RealisedConfigurationMetadata configuration = (RealisedConfigurationMetadata) getConfiguration(name);
            List<? extends DependencyMetadata> dependencies = configuration.getDependencies();
            ImmutableList.Builder<ModuleDependencyMetadata> transformedConfigurationDependencies = ImmutableList.builder();
            for (DependencyMetadata dependency : dependencies) {
                if (dependency instanceof IvyDependencyMetadata) {
                    IvyDependencyMetadata ivyDependency = (IvyDependencyMetadata) dependency;
                    IvyDependencyDescriptor newDescriptor = transformed.get(ivyDependency.getDependencyDescriptor());
                    transformedConfigurationDependencies.add(ivyDependency.withDescriptor(newDescriptor));
                } else {
                    transformedConfigurationDependencies.add((ModuleDependencyMetadata) dependency);
                }
            }
            transformedConfigurations.put(name, configuration.withDependencies(transformedConfigurationDependencies.build()));
        }
        return new RealisedIvyModuleResolveMetadata(this, transformedDescriptors, transformedConfigurations);
    }

}
