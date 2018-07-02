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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractRealisedModuleComponentResolveMetadata extends AbstractModuleComponentResolveMetadata {

    protected static ImmutableList<ImmutableRealisedVariantImpl> realiseVariants(ModuleComponentResolveMetadata mutableMetadata, VariantMetadataRules variantMetadataRules, ImmutableList<? extends ComponentVariant> variants) {
        List<ImmutableRealisedVariantImpl> realisedVariants = Lists.newArrayListWithExpectedSize(variants.size());
        for (ComponentVariant variant : variants) {
            ImmutableAttributes attributes = variantMetadataRules.applyVariantAttributeRules(variant, variant.getAttributes());
            CapabilitiesMetadata capabilitiesMetadata = variantMetadataRules.applyCapabilitiesRules(variant, variant.getCapabilities());
            List<GradleDependencyMetadata> dependencies = variantMetadataRules.applyDependencyMetadataRules(variant, convertDependencies(variant.getDependencies(), variant.getDependencyConstraints()));
            realisedVariants.add(new ImmutableRealisedVariantImpl(mutableMetadata.getId(), variant.getName(), attributes,
                variant.getDependencies(), variant.getDependencyConstraints(), variant.getFiles(),
                ImmutableCapabilities.of(capabilitiesMetadata.getCapabilities()), dependencies));
        }
        return ImmutableList.copyOf(realisedVariants);
    }

    static List<GradleDependencyMetadata> convertDependencies(List<? extends ComponentVariant.Dependency> dependencies, List<? extends ComponentVariant.DependencyConstraint> dependencyConstraints) {
        List<GradleDependencyMetadata> result = new ArrayList<GradleDependencyMetadata>(dependencies.size());
        for (ComponentVariant.Dependency dependency : dependencies) {
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getModule()), dependency.getVersionConstraint(), dependency.getAttributes());
            List<ExcludeMetadata> excludes = dependency.getExcludes();
            result.add(new GradleDependencyMetadata(selector, excludes, false, dependency.getReason()));
        }
        for (ComponentVariant.DependencyConstraint dependencyConstraint : dependencyConstraints) {
            result.add(new GradleDependencyMetadata(
                DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dependencyConstraint.getGroup(), dependencyConstraint.getModule()), dependencyConstraint.getVersionConstraint(), dependencyConstraint.getAttributes()),
                Collections.<ExcludeMetadata>emptyList(),
                true,
                dependencyConstraint.getReason()
            ));
        }
        return result;
    }

    public static ImmutableList<String> constructHierarchy(Configuration descriptorConfiguration, ImmutableMap<String, Configuration> configurationDefinitions) {
        if (descriptorConfiguration.getExtendsFrom().isEmpty()) {
            return ImmutableList.of(descriptorConfiguration.getName());
        }
        Set<String> accumulator = new LinkedHashSet<String>();
        populateHierarchy(descriptorConfiguration, configurationDefinitions, accumulator);
        return ImmutableList.copyOf(accumulator);
    }

    static void populateHierarchy(Configuration metadata, ImmutableMap<String, Configuration> configurationDefinitions, Set<String> accumulator) {
        accumulator.add(metadata.getName());
        for (String parentName : metadata.getExtendsFrom()) {
            Configuration parent = configurationDefinitions.get(parentName);
            populateHierarchy(parent, configurationDefinitions, accumulator);
        }
    }

    private Optional<ImmutableList<? extends ConfigurationMetadata>> graphVariants;
    private final ImmutableMap<String, ConfigurationMetadata> configurations;

    public AbstractRealisedModuleComponentResolveMetadata(AbstractRealisedModuleComponentResolveMetadata metadata) {
        super(metadata);
        this.configurations = metadata.configurations;
    }

    public AbstractRealisedModuleComponentResolveMetadata(AbstractRealisedModuleComponentResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.configurations = metadata.configurations;
    }

    public AbstractRealisedModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata mutableMetadata, ImmutableList<? extends ComponentVariant> variants,
                                                          Map<String, ConfigurationMetadata> configurations) {
        super(mutableMetadata, variants);
        this.configurations = ImmutableMap.<String, ConfigurationMetadata>builder().putAll(configurations).build();
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
        ImmutableList.Builder<ConfigurationMetadata> configurations = new ImmutableList.Builder<ConfigurationMetadata>();
        for (ComponentVariant variant : variants) {
            configurations.add(new RealisedVariantBackedConfigurationMetadata(getId(), variant, getAttributes(), getAttributesFactory()));
        }
        return Optional.<ImmutableList<? extends ConfigurationMetadata>>of(configurations.build());
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
        public DisplayName asDescribable() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
        }

        @Override
        public CapabilitiesMetadata getCapabilities() {
            throw new UnsupportedOperationException("NameOnlyVariantResolveMetadata cannot be used that way");
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

        public ImmutableRealisedVariantImpl(ModuleComponentIdentifier componentId, String name, ImmutableAttributes attributes,
                                     ImmutableList<? extends Dependency> dependencies, ImmutableList<? extends DependencyConstraint> dependencyConstraints,
                                     ImmutableList<? extends File> files, ImmutableCapabilities capabilities,
                                     List<GradleDependencyMetadata> dependencyMetadata) {
            this.componentId = componentId;
            this.name = name;
            this.attributes = attributes;
            this.dependencies = dependencies;
            this.dependencyConstraints = dependencyConstraints;
            this.files = files;
            this.capabilities = capabilities;
            this.dependencyMetadata = ImmutableList.copyOf(dependencyMetadata);
        }

        @Override
        public String getName() {
            return name;
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
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            List<ComponentArtifactMetadata> artifacts = new ArrayList<ComponentArtifactMetadata>(files.size());
            for (ComponentVariant.File file : files) {
                artifacts.add(new UrlBackedArtifactMetadata(componentId, file.getName(), file.getUri()));
            }
            return artifacts;
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
                && Objects.equal(files, that.files);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentId,
                name,
                attributes,
                dependencies,
                dependencyConstraints,
                files);
        }
    }

}
