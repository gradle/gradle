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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantResolveMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLocalComponentMetadata implements LocalComponentMetadata, BuildableLocalComponentMetadata {
    private final Map<String, DefaultLocalConfigurationMetadata> allConfigurations = Maps.newLinkedHashMap();
    private final Multimap<String, LocalComponentArtifactMetadata> allArtifacts = ArrayListMultimap.create();
    private final SetMultimap<String, DefaultVariantMetadata> allVariants = LinkedHashMultimap.create();
    private final ComponentIdentifier componentId;
    private final ModuleVersionIdentifier moduleVersionId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private Optional<ImmutableList<? extends ConfigurationMetadata>> consumableConfigurations;

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
    public DefaultLocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        DefaultLocalComponentMetadata copy = new DefaultLocalComponentMetadata(moduleVersionId, componentIdentifier, status, attributesSchema);
        for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
            copy.addConfiguration(configuration.getName(), configuration.description, configuration.extendsFrom, configuration.hierarchy, configuration.visible, configuration.transitive, configuration.attributes, configuration.canBeConsumed, configuration.canBeResolved, configuration.capabilities);
        }

        // Artifacts
        // Keep track of transformed artifacts as a given artifact may appear in multiple variants
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata>();
        for (Map.Entry<String, LocalComponentArtifactMetadata> entry : allArtifacts.entries()) {
            LocalComponentArtifactMetadata oldArtifact = entry.getValue();
            LocalComponentArtifactMetadata newArtifact = copyArtifact(oldArtifact, artifacts, transformedArtifacts);
            copy.allArtifacts.put(entry.getKey(), newArtifact);
        }

        // Variants
        for (Map.Entry<String, DefaultVariantMetadata> entry : allVariants.entries()) {
            DefaultVariantMetadata oldVariant = entry.getValue();
            List<LocalComponentArtifactMetadata> newArtifacts = new ArrayList<LocalComponentArtifactMetadata>(oldVariant.getArtifacts().size());
            for (ComponentArtifactMetadata oldArtifact : oldVariant.getArtifacts()) {
                newArtifacts.add(copyArtifact((LocalComponentArtifactMetadata) oldArtifact, artifacts, transformedArtifacts));
            }
            copy.allVariants.put(entry.getKey(), new DefaultVariantMetadata(oldVariant.asDescribable(), oldVariant.getAttributes(), newArtifacts, oldVariant.getCapabilities()));
        }

        for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
            configuration.realizeDependencies();
            DefaultLocalConfigurationMetadata configurationCopy = copy.allConfigurations.get(configuration.getName());

            // Dependencies
            configurationCopy.definedDependencies.addAll(configuration.definedDependencies);
            configurationCopy.definedFiles.addAll(configuration.definedFiles);

            // Exclude rules
            configurationCopy.definedExcludes.addAll(configuration.definedExcludes);
        }

        return copy;
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
    public void addArtifacts(String configuration, Iterable<? extends PublishArtifact> artifacts) {
        for (PublishArtifact artifact : artifacts) {
            LocalComponentArtifactMetadata artifactMetadata = new PublishArtifactLocalArtifactMetadata(componentId, artifact);
            allArtifacts.put(configuration, artifactMetadata);
        }
    }

    @Override
    public void addVariant(String configuration, OutgoingVariant variant) {
        List<LocalComponentArtifactMetadata> artifacts;
        if (variant.getArtifacts().isEmpty()) {
            artifacts = ImmutableList.of();
        } else {
            ImmutableList.Builder<LocalComponentArtifactMetadata> builder = ImmutableList.builder();
            for (PublishArtifact artifact : variant.getArtifacts()) {
                builder.add(new PublishArtifactLocalArtifactMetadata(componentId, artifact));
            }
            artifacts = builder.build();
        }
        allVariants.put(configuration, new DefaultVariantMetadata(variant.asDescribable(), variant.getAttributes().asImmutable(), artifacts, null));
    }

    @Override
    public BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, boolean canBeResolved, ImmutableCapabilities capabilities) {
        assert hierarchy.contains(name);
        DefaultLocalConfigurationMetadata conf = new DefaultLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, canBeResolved, capabilities);
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
    public ModuleSource getSource() {
        return null;
    }

    @Override
    public ComponentResolveMetadata withSource(ModuleSource source) {
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
    public ImmutableList<? extends ComponentIdentifier> getPlatformOwners() {
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
    public synchronized Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
        if (consumableConfigurations == null) {
            ImmutableList.Builder<ConfigurationMetadata> builder = new ImmutableList.Builder<ConfigurationMetadata>();
            boolean hasAtLeastOneConsumableConfiguration = false;
            for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
                if (configuration.isCanBeConsumed() && !configuration.getAttributes().isEmpty()) {
                    hasAtLeastOneConsumableConfiguration = true;
                    builder.add(configuration);
                }
            }
            if (hasAtLeastOneConsumableConfiguration) {
                consumableConfigurations = Optional.<ImmutableList<? extends ConfigurationMetadata>>of(builder.build());
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
    public AttributeContainer getAttributes() {
        // a local component cannot have attributes (for now). However, variants of the component
        // itself may.
        return ImmutableAttributes.EMPTY;
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
        private final boolean canBeResolved;
        private final ImmutableCapabilities capabilities;

        private ConfigurationInternal backingConfiguration;
        private LocalConfigurationMetadataBuilder configurationMetadataBuilder;

        private List<LocalOriginDependencyMetadata> definedDependencies = Lists.newArrayList();
        private List<ExcludeMetadata> definedExcludes = Lists.newArrayList();
        private List<LocalFileDependencyMetadata> definedFiles= Lists.newArrayList();

        private ImmutableList<LocalOriginDependencyMetadata> configurationDependencies;
        private ImmutableSet<LocalFileDependencyMetadata> configurationFileDependencies;
        private ImmutableList<ExcludeMetadata> configurationExcludes;

        private List<LocalComponentArtifactMetadata> configurationArtifacts;

        protected DefaultLocalConfigurationMetadata(String name,
                                                    String description,
                                                    boolean visible,
                                                    boolean transitive,
                                                    Set<String> extendsFrom,
                                                    ImmutableSet<String> hierarchy,
                                                    ImmutableAttributes attributes,
                                                    boolean canBeConsumed,
                                                    boolean canBeResolved,
                                                    ImmutableCapabilities capabilities) {
            this.name = name;
            this.description = description;
            this.transitive = transitive;
            this.visible = visible;
            this.hierarchy = hierarchy;
            this.extendsFrom = extendsFrom;
            this.attributes = attributes;
            this.canBeConsumed = canBeConsumed;
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
        public Set<? extends VariantResolveMetadata> getVariants() {
            return allVariants.get(name);
        }

        @Override
        public boolean isCanBeConsumed() {
            return canBeConsumed;
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
                maybeAddGeneratedDependencies(result);
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

        void maybeAddGeneratedDependencies(ImmutableList.Builder<LocalOriginDependencyMetadata> result) {
            // No-op
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
        public List<? extends LocalComponentArtifactMetadata> getArtifacts() {
            if (configurationArtifacts == null) {
                if (allArtifacts.isEmpty()) {
                    configurationArtifacts = ImmutableList.of();
                } else {
                    ImmutableList.Builder<LocalComponentArtifactMetadata> result = ImmutableList.builder();
                    for (String config : hierarchy) {
                        result.addAll(allArtifacts.get(config));
                    }
                    configurationArtifacts = result.build();
                }
            }
            return configurationArtifacts;
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

        private boolean include(DefaultLocalConfigurationMetadata configuration) {
            return hierarchy.contains(configuration.getName());
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
