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

package org.gradle.internal.component.external.model;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.CoercingStringValueSnapshot;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RealisedMavenModuleResolveMetadata extends AbstractRealisedModuleComponentResolveMetadata implements MavenModuleResolveMetadata {

    public static final String POM_PACKAGING = "pom";
    public static final Collection<String> JAR_PACKAGINGS = Arrays.asList("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");
    // We need to work with the 'String' version of the usage attribute, since this is expected for all providers by the `PreferJavaRuntimeVariant` schema
    private static final Attribute<String> USAGE_ATTRIBUTE = Attribute.of(Usage.USAGE_ATTRIBUTE.getName(), String.class);

    public static RealisedMavenModuleResolveMetadata transform(DefaultMavenModuleResolveMetadata metadata) {
        VariantMetadataRules variantMetadataRules = metadata.getVariantMetadataRules();
        ImmutableList<? extends ComponentVariant> variants = realiseVariants(metadata, variantMetadataRules, metadata.getVariants());


        boolean computeDerivedVariants = metadata.getVariants().size() == 0;
        List<ConfigurationMetadata> derivedVariants = Lists.newArrayListWithCapacity(2);
        Map<String, ConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(metadata.getConfigurationNames().size());
        for (String configurationName: metadata.getConfigurationNames()) {
            ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();
            Configuration configuration = configurationDefinitions.get(configurationName);

            NameOnlyVariantResolveMetadata variant = new NameOnlyVariantResolveMetadata(configurationName);
            ImmutableAttributes variantAttributes = variantMetadataRules.applyVariantAttributeRules(variant, metadata.getAttributes());
            CapabilitiesMetadata capabilitiesMetadata = variantMetadataRules.applyCapabilitiesRules(variant, ImmutableCapabilities.EMPTY);


            RealisedConfigurationMetadata realisedConfiguration = createConfiguration(variantMetadataRules, metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(),
                constructHierarchy(configuration, configurationDefinitions), metadata.getDependencies(), metadata.isImprovedPomSupportEnabled(),
                variantAttributes, ImmutableCapabilities.of(capabilitiesMetadata.getCapabilities()));
            configurations.put(configurationName, realisedConfiguration);

            if (computeDerivedVariants) {
                if (configurationName.equals("compile")) {
                    derivedVariants.add(withUsageAttribute(realisedConfiguration, Usage.JAVA_API, metadata.getAttributesFactory(), variantAttributes, metadata.getObjectInstantiator()));
                } else if (configurationName.equals("runtime")) {
                    derivedVariants.add(withUsageAttribute(realisedConfiguration, Usage.JAVA_RUNTIME, metadata.getAttributesFactory(), variantAttributes, metadata.getObjectInstantiator()));
                }
            }
        }
        return new RealisedMavenModuleResolveMetadata(metadata, variants, derivedVariants, configurations);
    }

    private static RealisedConfigurationMetadata createConfiguration(VariantMetadataRules variantMetadataRules, ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy, ImmutableList<MavenDependencyDescriptor> dependencies, boolean improvedPomSupport, ImmutableAttributes attributes, ImmutableCapabilities capabilities) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = getArtifactsForConfiguration(componentId, name);
        RealisedConfigurationMetadata configuration = new RealisedConfigurationMetadata(componentId, name, transitive, visible, hierarchy, artifacts, ImmutableList.<ExcludeMetadata>of(), attributes, capabilities);
        ImmutableList<ModuleDependencyMetadata> dependencyMetadata = filterDependencies(componentId, configuration, dependencies, improvedPomSupport);
        dependencyMetadata = ImmutableList.copyOf(variantMetadataRules.applyDependencyMetadataRules(new NameOnlyVariantResolveMetadata(name), dependencyMetadata));
        configuration.setDependencies(dependencyMetadata);
        return configuration;
    }

    public static ConfigurationMetadata withUsageAttribute(AbstractConfigurationMetadata conf, String usage, ImmutableAttributesFactory attributesFactory, ImmutableAttributes additionalAttributes, NamedObjectInstantiator instantiator) {
        ImmutableAttributes attributes = attributesFactory.concat(additionalAttributes, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, instantiator));
        return conf.withAttributes(attributes);
    }

    public static ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactsForConfiguration(ModuleComponentIdentifier id, String name) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;
        if (name.equals("compile") || name.equals("runtime") || name.equals("default") || name.equals("test")) {
            artifacts = ImmutableList.of(new DefaultModuleComponentArtifactMetadata(id, new DefaultIvyArtifactName(id.getModule(), "jar", "jar")));
        } else {
            artifacts = ImmutableList.of();
        }
        return artifacts;
    }

    private static ImmutableList<ModuleDependencyMetadata> filterDependencies(ModuleComponentIdentifier componentId, ConfigurationMetadata config, ImmutableList<MavenDependencyDescriptor> dependencies, boolean improvedPomSupport) {
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = ImmutableList.builder();
        boolean isOptionalConfiguration = "optional".equals(config.getName());

        for (MavenDependencyDescriptor dependency : dependencies) {
            if (isOptionalConfiguration && includeInOptionalConfiguration(dependency)) {
                filteredDependencies.add(new OptionalConfigurationDependencyMetadata(config, componentId, dependency));
            } else if (include(dependency, config.getHierarchy(), improvedPomSupport)) {
                filteredDependencies.add(contextualize(config, componentId, dependency, improvedPomSupport));
            }
        }
        return filteredDependencies.build();
    }

    public static ModuleDependencyMetadata contextualize(ConfigurationMetadata config, ModuleComponentIdentifier componentId, MavenDependencyDescriptor incoming, boolean improvedPomSupport) {
        ConfigurationBoundExternalDependencyMetadata dependency = new ConfigurationBoundExternalDependencyMetadata(config, componentId, incoming);
        if (improvedPomSupport) {
            dependency.alwaysUseAttributeMatching();
        }
        return dependency;
    }

    private static boolean includeInOptionalConfiguration(MavenDependencyDescriptor dependency) {
        MavenScope dependencyScope = dependency.getScope();
        // Include all 'optional' dependencies in "optional" configuration
        return dependency.isOptional()
            && dependencyScope != MavenScope.Test
            && dependencyScope != MavenScope.System;
    }

    private static boolean include(MavenDependencyDescriptor dependency, Collection<String> hierarchy, boolean improvedPomSupport) {
        MavenScope dependencyScope = dependency.getScope();
        if (dependency.isOptional() && !improvedPomSupport) {
            return false;
        }
        return hierarchy.contains(dependencyScope.getLowerName());
    }

    private final boolean improvedPomSupportEnabled;
    private final NamedObjectInstantiator objectInstantiator;

    private final ImmutableList<MavenDependencyDescriptor> dependencies;
    private final DefaultMavenModuleResolveMetadata metadata;
    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;

    private final ImmutableList<? extends ConfigurationMetadata> derivedVariants;

    public RealisedMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ImmutableList<? extends ComponentVariant> variants,
                                              List<ConfigurationMetadata> derivedVariants, Map<String, ConfigurationMetadata> configurations) {
        super(metadata, variants, configurations);
        this.improvedPomSupportEnabled = metadata.isImprovedPomSupportEnabled();
        this.objectInstantiator = metadata.getObjectInstantiator();
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        dependencies = metadata.getDependencies();
        this.metadata = metadata;
        this.derivedVariants = ImmutableList.copyOf(derivedVariants);
    }

    private RealisedMavenModuleResolveMetadata(RealisedMavenModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.improvedPomSupportEnabled = metadata.improvedPomSupportEnabled;
        this.objectInstantiator = metadata.objectInstantiator;
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
        dependencies = metadata.dependencies;
        this.derivedVariants = metadata.derivedVariants;
        this.metadata = metadata.metadata.withSource(source);
    }

    @Override
    protected Optional<ImmutableList<? extends ConfigurationMetadata>> maybeDeriveVariants() {
        return isJavaLibrary() ? Optional.<ImmutableList<? extends ConfigurationMetadata>>of(getDerivedVariants()) : Optional.<ImmutableList<? extends ConfigurationMetadata>>absent();
    }

    public ImmutableList<? extends ConfigurationMetadata> getDerivedVariants() {
        return derivedVariants;
    }

    @Override
    public RealisedMavenModuleResolveMetadata withSource(ModuleSource source) {
        return new RealisedMavenModuleResolveMetadata(this, source);
    }

    @Override
    public MutableMavenModuleResolveMetadata asMutable() {
        return metadata.asMutable();
    }

    public String getPackaging() {
        return packaging;
    }

    public boolean isRelocated() {
        return relocated;
    }

    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    public boolean isKnownJarPackaging() {
        return JAR_PACKAGINGS.contains(packaging);
    }

    private boolean isJavaLibrary() {
        return improvedPomSupportEnabled && (isKnownJarPackaging() || isPomPackaging());
    }

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

    /**
     * Adapts a MavenDependencyDescriptor to `DependencyMetadata` for the magic "optional" configuration.
     *
     * This configuration has special semantics:
     *  - Dependencies in the "optional" configuration are _never_ themselves optional (ie not 'pending')
     *  - Dependencies in the "optional" configuration can have dependency artifacts, even if the dependency is flagged as 'optional'.
     *    (For a standard configuration, any dependency flagged as 'optional' will have no dependency artifacts).
     */
    private static class OptionalConfigurationDependencyMetadata extends ConfigurationBoundExternalDependencyMetadata {
        private final MavenDependencyDescriptor dependencyDescriptor;

        public OptionalConfigurationDependencyMetadata(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, MavenDependencyDescriptor delegate) {
            super(configuration, componentId, delegate);
            this.dependencyDescriptor = delegate;
        }

        /**
         * Dependencies markes as optional/pending in the "optional" configuration _can_ have dependency artifacts.
         */
        @Override
        public List<IvyArtifactName> getArtifacts() {
            IvyArtifactName dependencyArtifact = dependencyDescriptor.getDependencyArtifact();
            return dependencyArtifact == null ? ImmutableList.<IvyArtifactName>of() : ImmutableList.of(dependencyArtifact);
        }

        /**
         * Dependencies in the "optional" configuration are never 'pending'.
         */
        @Override
        public boolean isPending() {
            return false;
        }
    }
}
