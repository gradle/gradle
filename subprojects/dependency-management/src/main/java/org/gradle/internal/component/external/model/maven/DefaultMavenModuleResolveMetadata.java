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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.CoercingStringValueSnapshot;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
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

/**
 * {@link AbstractLazyModuleComponentResolveMetadata Lazy version} of a {@link MavenModuleResolveMetadata}.
 *
 * @see RealisedMavenModuleResolveMetadata
 */
public class DefaultMavenModuleResolveMetadata extends AbstractLazyModuleComponentResolveMetadata implements MavenModuleResolveMetadata {

    public static final String POM_PACKAGING = "pom";
    static final Collection<String> JAR_PACKAGINGS = Arrays.asList("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");
    // We need to work with the 'String' version of the usage attribute, since this is expected for all providers by the `PreferJavaRuntimeVariant` schema
    static final Attribute<String> USAGE_ATTRIBUTE = Attribute.of(Usage.USAGE_ATTRIBUTE.getName(), String.class);

    private final NamedObjectInstantiator objectInstantiator;

    private final ImmutableList<MavenDependencyDescriptor> dependencies;
    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;

    private ImmutableList<? extends ConfigurationMetadata> derivedVariants;

    private boolean filterConstraints = true;

    DefaultMavenModuleResolveMetadata(DefaultMutableMavenModuleResolveMetadata metadata) {
        super(metadata);
        this.objectInstantiator = metadata.getObjectInstantiator();
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        dependencies = metadata.getDependencies();
    }

    private DefaultMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.objectInstantiator = metadata.objectInstantiator;
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
        dependencies = metadata.dependencies;

        copyCachedState(metadata);
    }

    @Override
    protected DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableSet<String> parents, VariantMetadataRules componentMetadataRules) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = getArtifactsForConfiguration(name);
        DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(componentId, name, transitive, visible, parents, artifacts, componentMetadataRules, ImmutableList.<ExcludeMetadata>of(), getAttributes());
        configuration.setDependencies(filterDependencies(configuration));
        return configuration;
    }

    @Override
    protected Optional<ImmutableList<? extends ConfigurationMetadata>> maybeDeriveVariants() {
        return isJavaLibrary() ? Optional.<ImmutableList<? extends ConfigurationMetadata>>of(getDerivedVariants()) : Optional.<ImmutableList<? extends ConfigurationMetadata>>absent();
    }

    private ImmutableList<? extends ConfigurationMetadata> getDerivedVariants() {
        if (derivedVariants == null) {
            filterConstraints = false;
            DefaultConfigurationMetadata compileConfiguration = (DefaultConfigurationMetadata) getConfiguration("compile");
            DefaultConfigurationMetadata runtimeConfiguration = (DefaultConfigurationMetadata) getConfiguration("runtime");
            derivedVariants = ImmutableList.of(
                libraryWithUsageAttribute(compileConfiguration, Usage.JAVA_API, getAttributesFactory()),
                libraryWithUsageAttribute(runtimeConfiguration, Usage.JAVA_RUNTIME, getAttributesFactory()),
                platformWithUsageAttribute(compileConfiguration, Usage.JAVA_API, getAttributesFactory(), false),
                platformWithUsageAttribute(runtimeConfiguration, Usage.JAVA_RUNTIME, getAttributesFactory(), false),
                platformWithUsageAttribute(compileConfiguration, Usage.JAVA_API, getAttributesFactory(), true),
                platformWithUsageAttribute(runtimeConfiguration, Usage.JAVA_RUNTIME, getAttributesFactory(), true));
        }
        return derivedVariants;
    }

    @Override
    protected ConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, ConfigurationMetadata> configurations) {
        DefaultConfigurationMetadata md = (DefaultConfigurationMetadata) super.populateConfigurationFromDescriptor(name, configurationDefinitions, configurations);
        if (filterConstraints && md != null) {
            // if the first call to getConfiguration is done before getDerivedVariants() is called
            // then it means we're using the legacy matching, without attributes, and that the metadata
            // we construct should _not_ include the constraints. We keep the constraints in the descriptors
            // because if we actually use attribute matching, we can select the platform variant which
            // does use constraints.
            return md.withoutConstraints();
        }
        return md;
    }

    private ConfigurationMetadata libraryWithUsageAttribute(DefaultConfigurationMetadata conf, String usage, ImmutableAttributesFactory attributesFactory) {
        ImmutableAttributes attributes = attributesFactory.concat(getAttributes().asImmutable(), USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, objectInstantiator));
        attributes = attributesFactory.concat(attributes, PlatformSupport.COMPONENT_CATEGORY, PlatformSupport.LIBRARY);
        return conf.withAttributes(attributes).withoutConstraints();
    }

    private ConfigurationMetadata platformWithUsageAttribute(DefaultConfigurationMetadata conf, String usage, ImmutableAttributesFactory attributesFactory, boolean enforcedPlatform) {
        ImmutableAttributes attributes = attributesFactory.concat(getAttributes().asImmutable(), USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, objectInstantiator));
        String componentType = enforcedPlatform ? PlatformSupport.ENFORCED_PLATFORM : PlatformSupport.REGULAR_PLATFORM;
        attributes = attributesFactory.concat(attributes, PlatformSupport.COMPONENT_CATEGORY, componentType);
        String prefix = enforcedPlatform ? "enforced-platform-" : "platform-";
        DefaultConfigurationMetadata metadata = conf.withAttributes(prefix + conf.getName(), attributes);
        metadata = metadata.withConstraintsOnly();
        if (enforcedPlatform) {
            metadata = metadata.withForcedDependencies();
        }
        return metadata;
    }

    private ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactsForConfiguration(String name) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;
        if (name.equals("compile") || name.equals("runtime") || name.equals("default") || name.equals("test")) {
            artifacts = ImmutableList.of(new DefaultModuleComponentArtifactMetadata(getId(), new DefaultIvyArtifactName(getId().getModule(), "jar", "jar")));
        } else {
            artifacts = ImmutableList.of();
        }
        return artifacts;
    }

    private ImmutableList<ModuleDependencyMetadata> filterDependencies(DefaultConfigurationMetadata config) {
        if (dependencies.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = null;
        boolean isOptionalConfiguration = "optional".equals(config.getName());
        ImmutableSet<String> hierarchy = config.getHierarchy();
        for (MavenDependencyDescriptor dependency : dependencies) {
            if (isOptionalConfiguration && includeInOptionalConfiguration(dependency)) {
                if (filteredDependencies == null) {
                    filteredDependencies = ImmutableList.builder();
                }
                filteredDependencies.add(new OptionalConfigurationDependencyMetadata(config, getId(), dependency));
            } else if (include(dependency, hierarchy)) {
                if (filteredDependencies == null) {
                    filteredDependencies = ImmutableList.builder();
                }
                filteredDependencies.add(contextualize(config, getId(), dependency));
            }
        }
        return filteredDependencies == null ? ImmutableList.<ModuleDependencyMetadata>of() : filteredDependencies.build();
    }

    private ModuleDependencyMetadata contextualize(ConfigurationMetadata config, ModuleComponentIdentifier componentId, MavenDependencyDescriptor incoming) {
        ConfigurationBoundExternalDependencyMetadata dependency = new ConfigurationBoundExternalDependencyMetadata(config, componentId, incoming);
        dependency.alwaysUseAttributeMatching();
        return dependency;
    }

    private boolean includeInOptionalConfiguration(MavenDependencyDescriptor dependency) {
        MavenScope dependencyScope = dependency.getScope();
        // Include all 'optional' dependencies in "optional" configuration
        return dependency.isOptional()
            && dependencyScope != MavenScope.Test
            && dependencyScope != MavenScope.System;
    }

    private boolean include(MavenDependencyDescriptor dependency, Collection<String> hierarchy) {
        if (dependency.isOptional()) {
            return false;
        }
        return hierarchy.contains(dependency.getScope().getLowerName());
    }

    @Override
    public DefaultMavenModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultMavenModuleResolveMetadata(this, source);
    }

    @Override
    public MutableMavenModuleResolveMetadata asMutable() {
        return new DefaultMutableMavenModuleResolveMetadata(this, objectInstantiator);
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

    public NamedObjectInstantiator getObjectInstantiator() {
        return objectInstantiator;
    }

    private boolean isJavaLibrary() {
        return isKnownJarPackaging() || isPomPackaging();
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
    protected VariantMetadataRules getVariantMetadataRules() {
        // Added for package visibility
        return super.getVariantMetadataRules();
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

        DefaultMavenModuleResolveMetadata that = (DefaultMavenModuleResolveMetadata) o;
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
     * - Dependencies in the "optional" configuration are _never_ themselves optional (ie not 'pending')
     * - Dependencies in the "optional" configuration can have dependency artifacts, even if the dependency is flagged as 'optional'.
     * (For a standard configuration, any dependency flagged as 'optional' will have no dependency artifacts).
     */
    static class OptionalConfigurationDependencyMetadata extends ConfigurationBoundExternalDependencyMetadata {
        private final MavenDependencyDescriptor dependencyDescriptor;

        OptionalConfigurationDependencyMetadata(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, MavenDependencyDescriptor delegate) {
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
        public boolean isConstraint() {
            return false;
        }
    }
}
