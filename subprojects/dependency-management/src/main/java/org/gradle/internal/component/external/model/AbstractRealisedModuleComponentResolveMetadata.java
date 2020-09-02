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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common base class for the realised versions of {@link ModuleComponentResolveMetadata} implementations.
 *
 * The realised part is about the application of {@link VariantMetadataRules} which are applied eagerly
 * to configuration or variant data.
 *
 * This type hierarchy is used whenever the {@code ModuleComponentResolveMetadata} needs to outlive
 * the build execution.
 */
public abstract class AbstractRealisedModuleComponentResolveMetadata extends AbstractModuleComponentResolveMetadata {

    private Optional<ImmutableList<? extends ConfigurationMetadata>> graphVariants;
    private final ImmutableMap<String, ConfigurationMetadata> configurations;

    public AbstractRealisedModuleComponentResolveMetadata(AbstractRealisedModuleComponentResolveMetadata metadata) {
        super(metadata);
        this.configurations = metadata.configurations;
    }

    public AbstractRealisedModuleComponentResolveMetadata(AbstractRealisedModuleComponentResolveMetadata metadata, ModuleSources sources, VariantDerivationStrategy derivationStrategy) {
        super(metadata, sources, derivationStrategy);
        this.configurations = metadata.configurations;
    }

    public AbstractRealisedModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata mutableMetadata, ImmutableList<? extends ComponentVariant> variants,
                                                          Map<String, ConfigurationMetadata> configurations) {
        super(mutableMetadata, variants);
        this.configurations = ImmutableMap.<String, ConfigurationMetadata>builder().putAll(configurations).build();
    }

    @Override
    public VariantMetadataRules getVariantMetadataRules() {
        return VariantMetadataRules.noOp();
    }

    @Override
    public Set<String> getConfigurationNames() {
        return configurations.keySet();
    }

    @Nullable
    @Override
    public ConfigurationMetadata getConfiguration(String name) {
        return configurations.get(name);
    }

    @Override
    public Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal(getVariants());
        }
        return graphVariants;
    }

    private Optional<ImmutableList<? extends ConfigurationMetadata>> buildVariantsForGraphTraversal(List<? extends ComponentVariant> variants) {
        if (variants.isEmpty()) {
            return maybeDeriveVariants();
        }
        ImmutableList.Builder<ConfigurationMetadata> configurations = new ImmutableList.Builder<>();
        for (ComponentVariant variant : variants) {
            configurations.add(new RealisedVariantBackedConfigurationMetadata(getId(), variant, getAttributes(), getAttributesFactory()));
        }
        return Optional.of(configurations.build());
    }

    protected static class NameOnlyVariantResolveMetadata implements VariantResolveMetadata {
        private final String name;

        public NameOnlyVariantResolveMetadata(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Identifier getIdentifier() {
            return null;
        }

        @Override
        public DisplayName asDescribable() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public CapabilitiesMetadata getCapabilities() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public boolean isExternalVariant() {
            return false;
        }
    }

    public static class ImmutableRealisedVariantImpl implements ComponentVariant, VariantResolveMetadata {
        private final ModuleComponentIdentifier componentId;
        private final String name;
        private final ImmutableAttributes attributes;
        private final ImmutableList<? extends Dependency> dependencies;
        private final ImmutableList<? extends DependencyConstraint> dependencyConstraints;
        private final ImmutableList<? extends File> files;
        private final ImmutableCapabilities capabilities;
        private final ImmutableList<GradleDependencyMetadata> dependencyMetadata;
        private final boolean externalVariant;

        public ImmutableRealisedVariantImpl(ModuleComponentIdentifier componentId, String name, ImmutableAttributes attributes,
                                            ImmutableList<? extends Dependency> dependencies, ImmutableList<? extends DependencyConstraint> dependencyConstraints,
                                            ImmutableList<? extends File> files, ImmutableCapabilities capabilities,
                                            List<GradleDependencyMetadata> dependencyMetadata,
                                            boolean externalVariant) {
            this.componentId = componentId;
            this.name = name;
            this.attributes = attributes;
            this.dependencies = dependencies;
            this.dependencyConstraints = dependencyConstraints;
            this.files = files;
            this.capabilities = capabilities;
            this.dependencyMetadata = ImmutableList.copyOf(dependencyMetadata);
            this.externalVariant = externalVariant;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Identifier getIdentifier() {
            return null;
        }

        @Override
        public DisplayName asDescribable() {
            return Describables.of(componentId, "variant", name);
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return attributes;
        }

        @Override
        public ImmutableList<? extends Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public ImmutableList<? extends DependencyConstraint> getDependencyConstraints() {
            return dependencyConstraints;
        }

        public ImmutableList<GradleDependencyMetadata> getDependencyMetadata() {
            return dependencyMetadata;
        }

        @Override
        public ImmutableList<? extends File> getFiles() {
            return files;
        }

        @Override
        public CapabilitiesMetadata getCapabilities() {
            return capabilities;
        }

        @Override
        public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
            ImmutableList.Builder<ComponentArtifactMetadata> artifacts = new ImmutableList.Builder<>();
            for (ComponentVariant.File file : files) {
                artifacts.add(new UrlBackedArtifactMetadata(componentId, file.getName(), file.getUri()));
            }
            return artifacts.build();
        }

        @Override
        public boolean isExternalVariant() {
            return externalVariant;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ImmutableRealisedVariantImpl that = (ImmutableRealisedVariantImpl) o;
            return Objects.equal(componentId, that.componentId)
                && Objects.equal(name, that.name)
                && Objects.equal(attributes, that.attributes)
                && Objects.equal(dependencies, that.dependencies)
                && Objects.equal(dependencyConstraints, that.dependencyConstraints)
                && Objects.equal(files, that.files)
                && externalVariant == that.externalVariant;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentId,
                name,
                attributes,
                dependencies,
                dependencyConstraints,
                files,
                externalVariant);
        }
    }

}
