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
import com.google.common.collect.Maps;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLocalComponentMetadata implements LocalComponentMetadata, BuildableLocalComponentMetadata {
    private final Map<String, DefaultLocalConfigurationMetadata> allConfigurations = Maps.newLinkedHashMap();
    private final ComponentIdentifier componentId;
    private final ModuleVersionIdentifier moduleVersionId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private final ModelContainer<?> model;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ModuleSources moduleSources = ImmutableModuleSources.of();

    private Optional<List<? extends VariantGraphResolveMetadata>> consumableConfigurations;

    public DefaultLocalComponentMetadata(ModuleVersionIdentifier moduleVersionId, ComponentIdentifier componentId, String status, AttributesSchemaInternal attributesSchema, ModelContainer<?> model, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.moduleVersionId = moduleVersionId;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
        this.model = model;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
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
        DefaultLocalComponentMetadata copy = new DefaultLocalComponentMetadata(moduleVersionId, componentIdentifier, status, attributesSchema, model, calculatedValueContainerFactory);
        for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
            copy.addConfiguration(configuration.getName(), configuration.getDescription(), configuration.getExtendsFrom(), configuration.getHierarchy(), configuration.isVisible(), configuration.isTransitive(), configuration.getAttributes(), configuration.isCanBeConsumed(), configuration.getConsumptionDeprecation(), configuration.isCanBeResolved(), configuration.getCapabilities());
        }

        // Artifacts
        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();

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
            configurationCopy.artifacts = calculatedValueContainerFactory.create(Describables.of(configurationCopy.getDescription(), "artifacts"), newArtifacts);
            configurationCopy.sourceArtifacts = null;

            for (LocalVariantMetadata oldVariant : configuration.getVariants()) {
                oldVariant.prepareToResolveArtifacts();
                ImmutableList<LocalComponentArtifactMetadata> newVariantArtifacts = copyArtifacts(oldVariant.getArtifacts(), artifacts, transformedArtifacts);
                configurationCopy.variants.add(new LocalVariantMetadata(oldVariant.getName(), oldVariant.getIdentifier(), oldVariant.asDescribable(), oldVariant.getAttributes(), newVariantArtifacts, (ImmutableCapabilities) oldVariant.getCapabilities(), calculatedValueContainerFactory));
            }
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
    public BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, DeprecationMessageBuilder.WithDocumentation consumptionDeprecation, boolean canBeResolved, ImmutableCapabilities capabilities) {
        assert hierarchy.contains(name);
        DefaultLocalConfigurationMetadata conf = new DefaultLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, consumptionDeprecation, canBeResolved, capabilities, model, calculatedValueContainerFactory, this);
        allConfigurations.put(name, conf);
        return conf;
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

    @Override
    public void reevaluate() {
        for (DefaultLocalConfigurationMetadata conf : allConfigurations.values()) {
            conf.reevaluate();
        }
    }

}
