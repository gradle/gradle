/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class DefaultLocalComponentMetadata implements LocalComponentMetadata, BuildableLocalComponentMetadata {
    private final Map<String, DefaultLocalConfigurationMetadata> allConfigurations = Maps.newLinkedHashMap();
    private final SetMultimap<String, LocalVariantMetadata> allVariants = LinkedHashMultimap.create();
    private final ComponentIdentifier componentId;
    private final ModuleVersionIdentifier moduleVersionId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private final ModuleSources moduleSources = ImmutableModuleSources.of();

    private Optional<List<? extends VariantGraphResolveMetadata>> consumableConfigurations;

    public DefaultLocalComponentMetadata(ModuleVersionIdentifier moduleVersionId, ComponentIdentifier componentId, String status, AttributesSchemaInternal attributesSchema) {
        this.moduleVersionId = moduleVersionId;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionId;
    }

    /**
     * Creates a copy of this metadata, transforming the artifacts and dependencies of this component.
     */
    @Override
    public DefaultLocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        DefaultLocalComponentMetadata copy = new DefaultLocalComponentMetadata(moduleVersionId, componentIdentifier, status, attributesSchema);
        for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
            copy.addConfiguration(configuration.getName(), configuration.description, configuration.extendsFrom, configuration.hierarchy, configuration.visible, configuration.transitive, configuration.attributes, configuration.canBeConsumed, configuration.consumptionDeprecation, configuration.canBeResolved, configuration.capabilities, Collections::emptyList);
        }

        // Artifacts
        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();

        // Variants
        for (Map.Entry<String, LocalVariantMetadata> entry : allVariants.entries()) {
            LocalVariantMetadata oldVariant = entry.getValue();
            oldVariant.prepareToResolveArtifacts();
            ImmutableList<LocalComponentArtifactMetadata> newArtifacts = copyArtifacts(oldVariant.getArtifacts(), artifacts, transformedArtifacts);
            copy.allVariants.put(entry.getKey(), new LocalVariantMetadata(oldVariant.getName(), oldVariant.getIdentifier(), oldVariant.asDescribable(), oldVariant.getAttributes(), newArtifacts, (ImmutableCapabilities) oldVariant.getCapabilities()));
        }

        for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
            configuration.realizeDependencies();
            configuration.prepareToResolveArtifacts();
            DefaultLocalConfigurationMetadata configurationCopy = copy.allConfigurations.get(configuration.getName());

            // Dependencies
            configurationCopy.definedDependencies.addAll(configuration.definedDependencies);
            configurationCopy.definedFiles.addAll(configuration.definedFiles);

            // Exclude rules
            configurationCopy.definedExcludes.addAll(configuration.definedExcludes);

            // Artifacts
            ImmutableList<LocalComponentArtifactMetadata> newArtifacts = copyArtifacts(configuration.getArtifacts(), artifacts, transformedArtifacts);
            configurationCopy.artifacts = newArtifacts;
            configurationCopy.sourceArtifacts = null;
        }

        return copy;
    }

    private ImmutableList<LocalComponentArtifactMetadata> copyArtifacts(List<LocalComponentArtifactMetadata> artifacts, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer, Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts) {
        if (artifacts.isEmpty()) {
            return ImmutableList.of();
        }

        ImmutableList.Builder<LocalComponentArtifactMetadata> newArtifacts = new ImmutableList.Builder<>();
        for (LocalComponentArtifactMetadata oldArtifact : artifacts) {
            newArtifacts.add(copyArtifact(oldArtifact, transformer, transformedArtifacts));
        }
        return newArtifacts.build();
    }

    private LocalComponentArtifactMetadata copyArtifact(LocalComponentArtifactMetadata oldArtifact, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer, Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts) {
        LocalComponentArtifactMetadata newArtifact = transformedArtifacts.get(oldArtifact);
        if (newArtifact == null) {
            newArtifact = transformer.transform(oldArtifact);
            transformedArtifacts.put(oldArtifact, newArtifact);
        }
        return newArtifact;
    }

    @Override
    public BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, DeprecationMessageBuilder.WithDocumentation consumptionDeprecation, boolean canBeResolved, ImmutableCapabilities capabilities, Supplier<List<DependencyConstraint>> consistentResolutionConstraints) {
        assert hierarchy.contains(name);
        DefaultLocalConfigurationMetadata conf = new DefaultLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionDeprecation, canBeResolved, capabilities);
        addToConfigurations(name, conf);
        return conf;
    }

    protected void addToConfigurations(String name, DefaultLocalConfigurationMetadata conf) {
        allConfigurations.put(name, conf);
    }

    @Override
    public void addDependenciesAndExcludesForConfiguration(ConfigurationInternal configuration, LocalConfigurationMetadataBuilder localConfigurationMetadataBuilder) {
        DefaultLocalConfigurationMetadata configurationMetadata = allConfigurations.get(configuration.getName());
        configurationMetadata.configurationMetadataBuilder = localConfigurationMetadataBuilder;
        configurationMetadata.backingConfiguration = configuration;
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
    }

    @Override
    public ModuleSources getSources() {
        return moduleSources;
    }

    @Override
    public ComponentResolveMetadata withSources(ModuleSources source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMissing() {
        return false;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public List<String> getStatusScheme() {
        return DEFAULT_STATUS_SCHEME;
    }

    @Override
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

    @Override
    public Set<String> getConfigurationNames() {
        return allConfigurations.keySet();
    }

    /**
     * For a local project component, the `variantsForGraphTraversal` are any _consumable_ configurations that have attributes defined.
     */
    @Override
    public synchronized Optional<List<? extends VariantGraphResolveMetadata>> getVariantsForGraphTraversal() {
        if (consumableConfigurations == null) {
            ImmutableList.Builder<VariantGraphResolveMetadata> builder = new ImmutableList.Builder<>();
            boolean hasAtLeastOneConsumableConfiguration = false;
            for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
                if (configuration.isCanBeConsumed() && !configuration.getAttributes().isEmpty()) {
                    hasAtLeastOneConsumableConfiguration = true;
                    builder.add(configuration);
                }
            }
            if (hasAtLeastOneConsumableConfiguration) {
                consumableConfigurations = Optional.of(builder.build());
            } else {
                consumableConfigurations = Optional.absent();
            }
        }
        return consumableConfigurations;
    }

    @Override
    public DefaultLocalConfigurationMetadata getConfiguration(final String name) {
        return allConfigurations.get(name);
    }

    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        // a local component cannot have attributes (for now). However, variants of the component
        // itself may.
        return ImmutableAttributes.EMPTY;
    }

    private class LocalVariantMetadata extends DefaultVariantMetadata {
        private Set<? extends PublishArtifact> sourceArtifacts;
        private ImmutableList<LocalComponentArtifactMetadata> artifacts;

        public LocalVariantMetadata(String name, Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> sourceArtifacts, ImmutableCapabilities capabilities) {
            super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
            this.sourceArtifacts = new LinkedHashSet<>(sourceArtifacts);
        }

        public LocalVariantMetadata(String name, Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableList<LocalComponentArtifactMetadata> artifacts, ImmutableCapabilities capabilities) {
            super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
            this.artifacts = artifacts;
        }

        public void prepareToResolveArtifacts() {
            synchronized (this) {
                if (artifacts == null) {
                    if (sourceArtifacts.isEmpty()) {
                        artifacts = ImmutableList.of();
                    } else {
                        ImmutableList.Builder<LocalComponentArtifactMetadata> result = ImmutableList.builderWithExpectedSize(sourceArtifacts.size());
                        for (PublishArtifact sourceArtifact : sourceArtifacts) {
                            result.add(new PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact));
                        }
                        artifacts = result.build();
                    }
                    sourceArtifacts = null;
                }
            }
        }

        @Override
        public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
            synchronized (this) {
                if (artifacts == null) {
                    throw new IllegalStateException();
                }
                return artifacts;
            }
        }
    }

    protected class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata, BuildableLocalConfigurationMetadata {
        private final String name;
        private final String description;
        private final boolean transitive;
        private final boolean visible;
        private final ImmutableSet<String> hierarchy;
        private final Set<String> extendsFrom;
        private final ImmutableAttributes attributes;
        private final boolean canBeConsumed;
        private final DeprecationMessageBuilder.WithDocumentation consumptionDeprecation;
        private final boolean canBeResolved;
        private final ImmutableCapabilities capabilities;

        private ConfigurationInternal backingConfiguration;
        private LocalConfigurationMetadataBuilder configurationMetadataBuilder;

        private final List<LocalOriginDependencyMetadata> definedDependencies = Lists.newArrayList();
        private final List<ExcludeMetadata> definedExcludes = Lists.newArrayList();
        private final List<LocalFileDependencyMetadata> definedFiles = Lists.newArrayList();

        private ImmutableList<LocalOriginDependencyMetadata> configurationDependencies;
        private ImmutableSet<LocalFileDependencyMetadata> configurationFileDependencies;
        private ImmutableList<ExcludeMetadata> configurationExcludes;

        private List<PublishArtifact> sourceArtifacts = Lists.newArrayList();
        private ImmutableList<LocalComponentArtifactMetadata> artifacts;

        protected DefaultLocalConfigurationMetadata(
            String name,
            String description,
            boolean visible,
            boolean transitive,
            Set<String> extendsFrom,
            ImmutableSet<String> hierarchy,
            ImmutableAttributes attributes,
            boolean canBeConsumed,
            DeprecationMessageBuilder.WithDocumentation consumptionDeprecation,
            boolean canBeResolved,
            ImmutableCapabilities capabilities
        ) {
            this.name = name;
            this.description = description;
            this.transitive = transitive;
            this.visible = visible;
            this.hierarchy = hierarchy;
            this.extendsFrom = extendsFrom;
            this.attributes = attributes;
            this.canBeConsumed = canBeConsumed;
            this.consumptionDeprecation = consumptionDeprecation;
            this.canBeResolved = canBeResolved;
            this.capabilities = capabilities;
        }

        @Override
        public ComponentIdentifier getComponentId() {
            return componentId;
        }

        @Override
        public void addDependency(LocalOriginDependencyMetadata dependency) {
            definedDependencies.add(dependency);
        }

        @Override
        public void addExclude(ExcludeMetadata exclude) {
            definedExcludes.add(exclude);
        }

        @Override
        public void addFiles(LocalFileDependencyMetadata files) {
            definedFiles.add(files);
        }

        @Override
        public void enableLocking() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return asDescribable().getDisplayName();
        }

        @Override
        public DisplayName asDescribable() {
            return Describables.of(componentId, "configuration", name);
        }

        public ComponentResolveMetadata getComponent() {
            return DefaultLocalComponentMetadata.this;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Set<String> getExtendsFrom() {
            return extendsFrom;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ImmutableSet<String> getHierarchy() {
            return hierarchy;
        }

        @Override
        public boolean isTransitive() {
            return transitive;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return attributes;
        }

        @Override
        public Set<? extends LocalVariantMetadata> getVariants() {
            return allVariants.get(name);
        }

        @Override
        public boolean isCanBeConsumed() {
            return canBeConsumed;
        }

        @Override
        public DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation() {
            return consumptionDeprecation;
        }

        @Override
        public boolean isCanBeResolved() {
            return canBeResolved;
        }

        @Override
        public List<? extends LocalOriginDependencyMetadata> getDependencies() {
            if (configurationDependencies == null) {
                ImmutableList.Builder<LocalOriginDependencyMetadata> result = ImmutableList.builder();
                for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
                    if (include(configuration)) {
                        configuration.addDefinedDependencies(result);
                    }
                }
                AttributeValue<Category> attributeValue = this.getAttributes().findEntry(Category.CATEGORY_ATTRIBUTE);
                if (attributeValue.isPresent() && attributeValue.get().getName().equals(Category.ENFORCED_PLATFORM)) {
                    // need to wrap all dependencies to force them
                    ImmutableList<LocalOriginDependencyMetadata> rawDependencies = result.build();
                    result = ImmutableList.builder();
                    for (LocalOriginDependencyMetadata rawDependency : rawDependencies) {
                        result.add(rawDependency.forced());
                    }
                }
                configurationDependencies = result.build();
            }
            return configurationDependencies;
        }

        List<LocalOriginDependencyMetadata> getSyntheticDependencies() {
            return Collections.emptyList();
        }

        void addDefinedDependencies(ImmutableList.Builder<LocalOriginDependencyMetadata> result) {
            realizeDependencies();
            result.addAll(definedDependencies);
        }

        @Override
        public Set<LocalFileDependencyMetadata> getFiles() {
            if (configurationFileDependencies == null) {
                ImmutableSet.Builder<LocalFileDependencyMetadata> result = ImmutableSet.builder();
                for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
                    if (include(configuration)) {
                        configuration.addDefinedFiles(result);
                    }
                }
                configurationFileDependencies = result.build();
            }
            return configurationFileDependencies;
        }

        void addDefinedFiles(ImmutableSet.Builder<LocalFileDependencyMetadata> result) {
            realizeDependencies();
            result.addAll(definedFiles);
        }

        @Override
        public ImmutableList<ExcludeMetadata> getExcludes() {
            if (configurationExcludes == null) {
                ImmutableList.Builder<ExcludeMetadata> result = ImmutableList.builder();
                for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
                    if (include(configuration)) {
                        configuration.addDefinedExcludes(result);
                    }
                }
                configurationExcludes = result.build();
            }
            return configurationExcludes;
        }

        void addDefinedExcludes(ImmutableList.Builder<ExcludeMetadata> result) {
            realizeDependencies();
            result.addAll(definedExcludes);
        }

        @Override
        public void addArtifacts(Collection<? extends PublishArtifact> artifacts) {
            sourceArtifacts.addAll(artifacts);
        }

        @Override
        public void prepareToResolveArtifacts() {
            synchronized (this) {
                if (artifacts == null) {
                    if (sourceArtifacts.isEmpty() && hierarchy.isEmpty()) {
                        artifacts = ImmutableList.of();
                    } else {
                        Set<LocalComponentArtifactMetadata> result = new LinkedHashSet<>();
                        for (PublishArtifact sourceArtifact : sourceArtifacts) {
                            result.add(new PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact));
                        }
                        for (String config : hierarchy) {
                            if (config.equals(name)) {
                                continue;
                            }
                            DefaultLocalConfigurationMetadata parent = allConfigurations.get(config);
                            parent.prepareToResolveArtifacts();
                            result.addAll(parent.getArtifacts());
                        }
                        artifacts = ImmutableList.copyOf(result);
                    }
                    sourceArtifacts = null;
                }
            }

            for (LocalVariantMetadata variant : getVariants()) {
                variant.prepareToResolveArtifacts();
            }
        }

        @Override
        public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
            synchronized (this) {
                if (artifacts == null) {
                    throw new IllegalStateException();
                }
                return artifacts;
            }
        }

        @Override
        public ComponentArtifactMetadata artifact(IvyArtifactName ivyArtifactName) {
            for (ComponentArtifactMetadata candidate : getArtifacts()) {
                if (candidate.getName().equals(ivyArtifactName)) {
                    return candidate;
                }
            }

            return new MissingLocalArtifactMetadata(componentId, ivyArtifactName);
        }

        @Override
        public CapabilitiesMetadata getCapabilities() {
            return capabilities;
        }

        @Override
        public boolean isExternalVariant() {
            return false;
        }

        private boolean include(DefaultLocalConfigurationMetadata configuration) {
            return hierarchy.contains(configuration.getName());
        }

        @Override
        public void addVariant(String name, VariantResolveMetadata.Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableCapabilities capabilities, Collection<? extends PublishArtifact> artifacts) {
            allVariants.put(this.name, new LocalVariantMetadata(name, identifier, displayName, attributes, artifacts, capabilities));
        }

        synchronized void realizeDependencies() {
            if (backingConfiguration != null) {
                backingConfiguration.runDependencyActions();
                configurationMetadataBuilder.addDependenciesAndExcludes(this, backingConfiguration);
                backingConfiguration = null;
            }
        }

    }
}
