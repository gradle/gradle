/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata, BuildableLocalConfigurationMetadata, LocalConfigurationGraphResolveMetadata {
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
    private final ModelContainer<?> model;
    private final CalculatedValueContainerFactory factory;
    private final DefaultLocalComponentMetadata component;

    public ConfigurationInternal backingConfiguration;
    private boolean reevaluate = true;
    public LocalConfigurationMetadataBuilder configurationMetadataBuilder;

    public final List<LocalOriginDependencyMetadata> definedDependencies = Lists.newArrayList();
    public final List<ExcludeMetadata> definedExcludes = Lists.newArrayList();
    public final List<LocalFileDependencyMetadata> definedFiles = Lists.newArrayList();

    private ImmutableList<LocalOriginDependencyMetadata> configurationDependencies;
    private ImmutableSet<LocalFileDependencyMetadata> configurationFileDependencies;
    private ImmutableList<ExcludeMetadata> configurationExcludes;

    public List<PublishArtifact> sourceArtifacts = Lists.newArrayList();
    public CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts;

    public final Set<LocalVariantMetadata> variants = new LinkedHashSet<>();

    public DefaultLocalConfigurationMetadata(
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
        ImmutableCapabilities capabilities,
        ModelContainer<?> model,
        CalculatedValueContainerFactory factory,
        DefaultLocalComponentMetadata component
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
        this.model = model;
        this.factory = factory;
        this.component = component;
        this.artifacts = factory.create(Describables.of(description, "artifacts"), context -> {
            if (sourceArtifacts.isEmpty() && hierarchy.isEmpty()) {
                sourceArtifacts = null;
                return ImmutableList.of();
            } else {
                return model.fromMutableState(m -> {
                    Set<LocalComponentArtifactMetadata> result = new LinkedHashSet<>(sourceArtifacts.size());
                    for (PublishArtifact sourceArtifact : sourceArtifacts) {
                        result.add(new PublishArtifactLocalArtifactMetadata(component.getId(), sourceArtifact));
                    }
                    for (String config : hierarchy) {
                        if (config.equals(name)) {
                            continue;
                        }
                        DefaultLocalConfigurationMetadata parent = component.getConfiguration(config);
                        parent.prepareToResolveArtifacts();
                        result.addAll(parent.getArtifacts());
                    }
                    sourceArtifacts = null;
                    return ImmutableList.copyOf(result);
                });
            }
        });
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return component.getId();
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
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(component.getId(), "configuration", name);
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
    public Set<LocalVariantMetadata> getVariants() {
        return variants;
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
            for (String configurationName : component.getConfigurationNames()) {
                if (include(configurationName)) {
                    component.getConfiguration(configurationName).addDefinedDependencies(result);
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

    void addDefinedDependencies(ImmutableList.Builder<LocalOriginDependencyMetadata> result) {
        realizeDependencies();
        result.addAll(definedDependencies);
    }

    @Override
    public Set<LocalFileDependencyMetadata> getFiles() {
        if (configurationFileDependencies == null) {
            ImmutableSet.Builder<LocalFileDependencyMetadata> result = ImmutableSet.builder();
            for (String configurationName : component.getConfigurationNames()) {
                if (include(configurationName)) {
                    component.getConfiguration(configurationName).addDefinedFiles(result);
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
            for (String configurationName : component.getConfigurationNames()) {
                if (include(configurationName)) {
                    component.getConfiguration(configurationName).addDefinedExcludes(result);
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
    public LocalConfigurationMetadata prepareToResolveArtifacts() {
        artifacts.finalizeIfNotAlready();
        for (LocalVariantMetadata variant : variants) {
            variant.prepareToResolveArtifacts();
        }
        return this;
    }

    @Override
    public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
        return artifacts.get();
    }

    @Override
    public ComponentArtifactMetadata artifact(IvyArtifactName ivyArtifactName) {
        for (ComponentArtifactMetadata candidate : getArtifacts()) {
            if (candidate.getName().equals(ivyArtifactName)) {
                return candidate;
            }
        }

        return new MissingLocalArtifactMetadata(component.getId(), ivyArtifactName);
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean isExternalVariant() {
        return false;
    }

    private boolean include(String configurationName) {
        return hierarchy.contains(configurationName);
    }

    @Override
    public void addVariant(String name, VariantResolveMetadata.Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableCapabilities capabilities, Collection<? extends PublishArtifact> artifacts) {
        variants.add(new LocalVariantMetadata(name, identifier, component.getId(), displayName, attributes, artifacts, capabilities, model, factory));
    }

    synchronized void realizeDependencies() {
        if (reevaluate && backingConfiguration != null) {
            backingConfiguration.runDependencyActions();
            configurationMetadataBuilder.addDependenciesAndExcludes(this, backingConfiguration);
        }
        reevaluate = false;
    }

    /**
     * When the backing configuration could have been modified, we need to clear our retained cache/state,
     * so that the next evaluation is clean.
     */
    synchronized void reevaluate() {
        definedDependencies.clear();
        definedFiles.clear();
        definedExcludes.clear();
        configurationDependencies = null;
        configurationExcludes = null;
        configurationFileDependencies = null;
        reevaluate = true;
    }

    @Override
    public boolean needsReevaluate() {
        return reevaluate;
    }
}
