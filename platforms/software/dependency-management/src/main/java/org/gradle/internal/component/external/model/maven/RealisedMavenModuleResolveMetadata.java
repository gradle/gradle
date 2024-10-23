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

package org.gradle.internal.component.external.model.maven;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.PomReader;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AdditionalVariant;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ExternalVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata.JAR_PACKAGINGS;
import static org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata.POM_PACKAGING;

/**
 * {@link AbstractRealisedModuleComponentResolveMetadata Realised version} of a {@link MavenModuleResolveMetadata}.
 *
 * @see DefaultMavenModuleResolveMetadata
 */
public class RealisedMavenModuleResolveMetadata extends AbstractRealisedModuleComponentResolveMetadata implements MavenModuleResolveMetadata {

    /**
     * Factory method to transform a {@link DefaultMavenModuleResolveMetadata}, which is lazy, into a realised version.
     *
     * @param metadata the lazy metadata to transform
     * @return the realised version of the metadata
     */
    public static RealisedMavenModuleResolveMetadata transform(DefaultMavenModuleResolveMetadata metadata) {
        VariantMetadataRules variantMetadataRules = metadata.getVariantMetadataRules();
        ImmutableList<? extends ComponentVariant> variants = LazyToRealisedModuleComponentResolveMetadataHelper.realiseVariants(metadata, variantMetadataRules, metadata.getVariants());
        Map<String, ModuleConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(metadata.getConfigurationNames().size());
        ImmutableList<ModuleConfigurationMetadata> derivedVariants = ImmutableList.of();
        if (variants.isEmpty()) {
            Optional<List<? extends ModuleConfigurationMetadata>> sourceVariants = metadata.deriveVariants();
            if (sourceVariants.isPresent()) {
                ImmutableList.Builder<ModuleConfigurationMetadata> builder = new ImmutableList.Builder<>();
                for (ConfigurationMetadata sourceVariant : sourceVariants.get()) {
                    ImmutableList<ModuleDependencyMetadata> dependencies = Cast.uncheckedCast(sourceVariant.getDependencies());
                    // We do not need to apply the rules manually to derived variants, because the derivation already
                    // instantiated 'derivedVariant' as 'DefaultConfigurationMetadata' which does the rules application
                    // automatically when calling the getters (done in the code below).
                    RealisedConfigurationMetadata derivedVariantMetadata = new RealisedConfigurationMetadata(
                        metadata.getId(),
                        sourceVariant.getName(),
                        sourceVariant.isTransitive(),
                        sourceVariant.isVisible(),
                        sourceVariant.getHierarchy(),
                        Cast.uncheckedCast(sourceVariant.getArtifacts()),
                        sourceVariant.getExcludes(),
                        sourceVariant.getAttributes(),
                        sourceVariant.getCapabilities(),
                        dependencies,
                        false,
                        sourceVariant.isExternalVariant()
                    );
                    builder.add(derivedVariantMetadata);
                }
                derivedVariants = builder.build();
            }
            derivedVariants = addVariantsFromRules(metadata, derivedVariants, variantMetadataRules);
        }
        for (String configurationName : metadata.getConfigurationNames()) {
            configurations.put(configurationName, createConfiguration(metadata, configurationName));
        }
        return new RealisedMavenModuleResolveMetadata(metadata, variants, derivedVariants, configurations);
    }

    private static ImmutableList<ModuleConfigurationMetadata> addVariantsFromRules(
        ModuleComponentResolveMetadata componentMetadata,
        ImmutableList<ModuleConfigurationMetadata> derivedVariants,
        VariantMetadataRules variantMetadataRules
    ) {
        List<AdditionalVariant> additionalVariants = variantMetadataRules.getAdditionalVariants();
        if (additionalVariants.isEmpty()) {
            return derivedVariants;
        }
        ImmutableList.Builder<ModuleConfigurationMetadata> builder = new ImmutableList.Builder<>();
        builder.addAll(derivedVariants);
        Map<String, ModuleConfigurationMetadata> variantsByName = derivedVariants.stream().collect(Collectors.toMap(ConfigurationMetadata::getName, Function.identity()));
        for (AdditionalVariant additionalVariant : additionalVariants) {
            String name = additionalVariant.getName();
            String baseName = additionalVariant.getBase();
            ImmutableAttributes attributes;
            ImmutableCapabilities capabilities;
            List<? extends ModuleDependencyMetadata> dependencies;
            ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;

            ModuleConfigurationMetadata baseConf = variantsByName.get(baseName);
            if (baseConf == null) {
                attributes = componentMetadata.getAttributes();
                capabilities = ImmutableCapabilities.EMPTY;
                dependencies = ImmutableList.of();
                artifacts = ImmutableList.of();
            } else {
                attributes = baseConf.getAttributes();
                capabilities = baseConf.getCapabilities();
                dependencies = baseConf.getDependencies();
                artifacts = Cast.uncheckedCast(baseConf.getArtifacts());
            }

            if (baseName == null || baseConf != null) {
                builder.add(applyRules(componentMetadata.getId(), name, variantMetadataRules, attributes, capabilities, dependencies, artifacts, true, true, ImmutableSet.of(), true, false));
            } else if (!additionalVariant.isLenient()) {
                throw new InvalidUserDataException("Variant '" + baseName + "' not defined in module " + componentMetadata.getId().getDisplayName());
            }
        }
        return builder.build();
    }

    private static RealisedConfigurationMetadata applyRules(
        ModuleComponentIdentifier id,
        String configurationName,
        VariantMetadataRules variantMetadataRules,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        List<? extends ModuleDependencyMetadata> dependencies,
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
        boolean transitive,
        boolean visible,
        ImmutableSet<String> hierarchy,
        boolean addedByRule,
        boolean isExternalVariant
    ) {
        NameOnlyVariantResolveMetadata variant = new NameOnlyVariantResolveMetadata(configurationName);
        ImmutableAttributes variantAttributes = variantMetadataRules.applyVariantAttributeRules(variant, attributes);
        ImmutableCapabilities variantCapabilities = variantMetadataRules.applyCapabilitiesRules(variant, capabilities);
        List<? extends DependencyMetadata> dependenciesMetadata = variantMetadataRules.applyDependencyMetadataRules(variant, dependencies);
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifactsMetadata = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts(variant, artifacts, id);
        return createConfiguration(id, configurationName, transitive, visible, hierarchy, artifactsMetadata, dependenciesMetadata, variantAttributes, variantCapabilities, addedByRule, isExternalVariant);
    }

    private static RealisedConfigurationMetadata createConfiguration(DefaultMavenModuleResolveMetadata metadata, String configurationName) {
        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();
        Configuration configuration = metadata.getConfigurationDefinitions().get(configurationName);
        ImmutableSet<String> hierarchy = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration, configurationDefinitions);
        return createConfiguration(metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(), hierarchy,
            getArtifactsForConfiguration(metadata), metadata.getConfiguration(configurationName).getDependencies(),
            metadata.getAttributes(), ImmutableCapabilities.EMPTY, false, metadata.isExternalVariant());
    }

    private static RealisedConfigurationMetadata createConfiguration(
        ModuleComponentIdentifier componentId,
        String name,
        boolean transitive,
        boolean visible,
        ImmutableSet<String> hierarchy,
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
        List<? extends DependencyMetadata> dependencies,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        boolean addedByRule,
        boolean isExternalVariant
    ) {
        ImmutableList<ModuleDependencyMetadata> asImmutable = ImmutableList.copyOf(Cast.<List<ModuleDependencyMetadata>>uncheckedCast(dependencies));
        return new RealisedConfigurationMetadata(componentId, name, transitive, visible, hierarchy, artifacts, ImmutableList.of(), attributes, capabilities, asImmutable, addedByRule, isExternalVariant);
    }

    static ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactsForConfiguration(DefaultMavenModuleResolveMetadata metadata) {
        if (metadata.isRelocated()) {
            // relocated packages have no artifacts
            return ImmutableList.of();
        } else if (metadata.isPomPackaging()) {
            // Modules with POM packaging _may_ have a jar
            return ImmutableList.of(metadata.optionalArtifact("jar", "jar", null));
        } else if (metadata.isKnownJarPackaging()) {
            // Modules with a type of packaging that's always a jar
            return ImmutableList.of(metadata.artifact("jar", "jar", null));
        } else {
            String type = metadata.getPackaging();
            // We were unable to resolve variable substitutions in the POM, so assume we're looking for a jar
            if (PomReader.hasUnresolvedSubstitutions(type)) {
                return ImmutableList.of(metadata.artifact("jar", "jar", null));
            } else {
                // Modules with other types of packaging may publish an artifact with that extension or a jar
                return ImmutableList.of(new DefaultModuleComponentArtifactMetadata(metadata.getId(), new DefaultIvyArtifactName(metadata.getId().getModule(), type, type),
                    metadata.artifact("jar", "jar", null)));
            }
        }
    }

    private final NamedObjectInstantiator objectInstantiator;

    private final ImmutableList<MavenDependencyDescriptor> dependencies;
    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;

    private final ImmutableList<? extends ModuleConfigurationMetadata> derivedVariants;

    RealisedMavenModuleResolveMetadata(
        DefaultMavenModuleResolveMetadata metadata, ImmutableList<? extends ComponentVariant> variants,
        List<ModuleConfigurationMetadata> derivedVariants, Map<String, ModuleConfigurationMetadata> configurations
    ) {
        super(metadata, variants, configurations);
        this.objectInstantiator = metadata.getObjectInstantiator();
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        dependencies = metadata.getDependencies();
        this.derivedVariants = ImmutableList.copyOf(derivedVariants);
    }

    private RealisedMavenModuleResolveMetadata(RealisedMavenModuleResolveMetadata metadata, ModuleSources sources, VariantDerivationStrategy derivationStrategy) {
        super(metadata, sources, derivationStrategy);
        this.objectInstantiator = metadata.objectInstantiator;
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
        dependencies = metadata.dependencies;
        this.derivedVariants = metadata.derivedVariants;
    }

    @Override
    protected Optional<List<? extends ExternalVariantGraphResolveMetadata>> maybeDeriveVariants() {
        return Optional.of(getDerivedVariants());
    }

    ImmutableList<? extends ModuleConfigurationMetadata> getDerivedVariants() {
        return derivedVariants;
    }

    @Override
    public RealisedMavenModuleResolveMetadata withSources(ModuleSources sources) {
        return new RealisedMavenModuleResolveMetadata(this, sources, getVariantDerivationStrategy());
    }

    @Override
    public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
        if (getVariantDerivationStrategy() == derivationStrategy) {
            return this;
        }
        return new RealisedMavenModuleResolveMetadata(this, getSources(), derivationStrategy);
    }

    @Override
    public MutableMavenModuleResolveMetadata asMutable() {
        return new DefaultMutableMavenModuleResolveMetadata(this, objectInstantiator);
    }

    @Override
    public String getPackaging() {
        return packaging;
    }

    @Override
    public boolean isRelocated() {
        return relocated;
    }

    @Override
    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    @Override
    public boolean isKnownJarPackaging() {
        return JAR_PACKAGINGS.contains(packaging);
    }

    @Override
    @Nullable
    public String getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    @Override
    public ImmutableList<MavenDependencyDescriptor> getDependencies() {
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

        RealisedMavenModuleResolveMetadata that = (RealisedMavenModuleResolveMetadata) o;
        return relocated == that.relocated
            && Objects.equal(dependencies, that.dependencies)
            && Objects.equal(packaging, that.packaging)
            && Objects.equal(snapshotTimestamp, that.snapshotTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(),
            dependencies,
            packaging,
            relocated,
            snapshotTimestamp);
    }
}
