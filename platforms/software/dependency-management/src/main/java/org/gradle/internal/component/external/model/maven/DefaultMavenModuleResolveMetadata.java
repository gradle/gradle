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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.ExternalModuleVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link AbstractLazyModuleComponentResolveMetadata Lazy version} of a {@link MavenModuleResolveMetadata}.
 *
 * @see RealisedMavenModuleResolveMetadata
 */
public class DefaultMavenModuleResolveMetadata extends AbstractLazyModuleComponentResolveMetadata implements MavenModuleResolveMetadata {

    public static final String POM_PACKAGING = "pom";
    static final Set<String> JAR_PACKAGINGS = ImmutableSet.of("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");

    private final NamedObjectInstantiator objectInstantiator;
    private final MavenAttributesFactory mavenAttributesFactory;

    private final ImmutableList<MavenDependencyDescriptor> dependencies;
    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;

    private ImmutableList<? extends ModuleConfigurationMetadata> derivedVariants;

    private boolean filterConstraints = true;
    private MavenDependencyDescriptor[] dependenciesAsArray;

    DefaultMavenModuleResolveMetadata(DefaultMutableMavenModuleResolveMetadata metadata) {
        super(metadata);
        this.objectInstantiator = metadata.getObjectInstantiator();
        this.mavenAttributesFactory = (MavenAttributesFactory) metadata.getAttributesFactory();
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        dependencies = metadata.getDependencies();
    }

    private DefaultMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ModuleSources sources, VariantDerivationStrategy derivationStrategy) {
        super(metadata, sources, derivationStrategy);
        this.objectInstantiator = metadata.objectInstantiator;
        this.mavenAttributesFactory = metadata.mavenAttributesFactory;
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
        dependencies = metadata.dependencies;

        copyCachedState(metadata, metadata.getVariantDerivationStrategy() != derivationStrategy);
    }

    @Override
    protected DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableSet<String> parents, VariantMetadataRules componentMetadataRules) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = getArtifactsForConfiguration();
        final DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(componentId, name, transitive, visible, parents, artifacts, componentMetadataRules, ImmutableList.of(), getAttributes(), false);
        configuration.setConfigDependenciesFactory(() -> filterDependencies(configuration));
        return configuration;
    }

    @Override
    protected Optional<List<? extends ExternalModuleVariantGraphResolveMetadata>> maybeDeriveVariants() {
        return Optional.ofNullable(getDerivedVariants());
    }

    protected Optional<List<? extends ModuleConfigurationMetadata>> deriveVariants() {
        return Optional.ofNullable(getDerivedVariants());
    }

    private ImmutableList<? extends ModuleConfigurationMetadata> getDerivedVariants() {
        VariantDerivationStrategy strategy = getVariantDerivationStrategy();
        if (derivedVariants == null && strategy.derivesVariants()) {
            filterConstraints = false;
            derivedVariants = strategy.derive(this);
        }
        return derivedVariants;
    }

    @Override
    protected ModuleConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions) {
        DefaultConfigurationMetadata md = (DefaultConfigurationMetadata) super.populateConfigurationFromDescriptor(name, configurationDefinitions);
        if (filterConstraints && md != null) {
            // if the first call to getConfiguration is done before getDerivedVariants() is called
            // then it means we're using the legacy matching, without attributes, and that the metadata
            // we construct should _not_ include the constraints. We keep the constraints in the descriptors
            // because if we actually use attribute matching, we can select the platform variant which
            // does use constraints.
            return md.mutate().withoutConstraints().build();
        }
        return md;
    }

    private ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactsForConfiguration() {
        return RealisedMavenModuleResolveMetadata.getArtifactsForConfiguration(this);
    }

    private ImmutableList<ModuleDependencyMetadata> filterDependencies(DefaultConfigurationMetadata config) {
        if (dependencies.isEmpty()) {
            return ImmutableList.of();
        }
        int size = dependencies.size();
        // If we're reaching this point, we're very likely going to iterate on the dependencies
        // several times. It appears that iterating using `dependencies` is expensive because of
        // the creation of an iterator and checking bounds. Iterating an array is faster.
        if (dependenciesAsArray == null) {
            dependenciesAsArray = dependencies.toArray(new MavenDependencyDescriptor[0]);
        }
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = null;
        boolean isOptionalConfiguration = "optional".equals(config.getName());
        ImmutableSet<String> hierarchy = config.getHierarchy();
        for (MavenDependencyDescriptor dependency : dependenciesAsArray) {
            if (isOptionalConfiguration && includeInOptionalConfiguration(dependency)) {
                ModuleDependencyMetadata element = new OptionalConfigurationMavenDependencyMetadata(dependency);
                if (size == 1) {
                    return ImmutableList.of(element);
                }
                if (filteredDependencies == null) {
                    filteredDependencies = ImmutableList.builder();
                }
                filteredDependencies.add(element);
            } else if (include(dependency, hierarchy)) {
                ModuleDependencyMetadata element = new MavenDependencyMetadata(dependency);
                if (size == 1) {
                    return ImmutableList.of(element);
                }
                if (filteredDependencies == null) {
                    filteredDependencies = ImmutableList.builder();
                }
                filteredDependencies.add(element);
            }
        }
        return filteredDependencies == null ? ImmutableList.of() : filteredDependencies.build();
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
    public MutableMavenModuleResolveMetadata asMutable() {
        return new DefaultMutableMavenModuleResolveMetadata(this, objectInstantiator);
    }

    @Override
    public DefaultMavenModuleResolveMetadata withSources(ModuleSources sources) {
        return new DefaultMavenModuleResolveMetadata(this, sources, getVariantDerivationStrategy());
    }

    @Override
    public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
        if (getVariantDerivationStrategy() == derivationStrategy) {
            return this;
        }
        return new DefaultMavenModuleResolveMetadata(this, getSources(), derivationStrategy);
    }

    @Override
    public @NonNull String getPackaging() {
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

    public NamedObjectInstantiator getObjectInstantiator() {
        return objectInstantiator;
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
    private static class OptionalConfigurationMavenDependencyMetadata extends MavenDependencyMetadata {
        OptionalConfigurationMavenDependencyMetadata(MavenDependencyDescriptor delegate) {
            super(delegate);
        }

        /**
         * Dependencies marked as optional/pending in the "optional" configuration _can_ have dependency artifacts.
         */
        @Override
        public List<IvyArtifactName> getArtifacts() {
            IvyArtifactName dependencyArtifact = getDependencyDescriptor().getDependencyArtifact();
            return dependencyArtifact == null ? ImmutableList.of() : ImmutableList.of(dependencyArtifact);
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
