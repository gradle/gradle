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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DefaultLocalComponentMetadata extends AbstractLocalComponentMetadata<DefaultLocalComponentMetadata.DefaultLocalConfigurationMetadata> implements LocalComponentMetadata {

    public DefaultLocalComponentMetadata(ModuleVersionIdentifier moduleVersionId, ComponentIdentifier componentId, String status, AttributesSchemaInternal attributesSchema, ModelContainer<?> model, CalculatedValueContainerFactory calculatedValueContainerFactory, LocalConfigurationMetadataBuilder configurationMetadataBuilder) {
        super(moduleVersionId, componentId, status, attributesSchema, model, calculatedValueContainerFactory, configurationMetadataBuilder);
    }

    @Override
    public List<? extends DependencyMetadata> getSyntheticDependencies(String configuration) {
        return Collections.emptyList();
    }

    @Override
    public DefaultLocalConfigurationMetadata createConfiguration(
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
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        Supplier<List<DependencyConstraint>> consistentResolutionConstraints
    ) {
        return new DefaultLocalConfigurationMetadata(
            name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed,
            consumptionDeprecation, canBeResolved, capabilities, model, calculatedValueContainerFactory, this
        );
    }

    private static class LocalVariantMetadata extends DefaultVariantMetadata {
        private final CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts;

        public LocalVariantMetadata(String name, Identifier identifier, ComponentIdentifier componentId, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> sourceArtifacts, ImmutableCapabilities capabilities, ModelContainer<?> model, CalculatedValueContainerFactory calculatedValueContainerFactory) {
            super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
            artifacts = calculatedValueContainerFactory.create(Describables.of(displayName, "artifacts"), context -> {
                if (sourceArtifacts.isEmpty()) {
                    return ImmutableList.of();
                } else {
                    return model.fromMutableState(m -> {
                        ImmutableList.Builder<LocalComponentArtifactMetadata> result = ImmutableList.builderWithExpectedSize(sourceArtifacts.size());
                        for (PublishArtifact sourceArtifact : sourceArtifacts) {
                            result.add(new PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact));
                        }
                        return result.build();
                    });
                }
            });
        }

        public LocalVariantMetadata(String name, Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableList<LocalComponentArtifactMetadata> artifacts, ImmutableCapabilities capabilities, CalculatedValueContainerFactory calculatedValueContainerFactory) {
            super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
            this.artifacts = calculatedValueContainerFactory.create(Describables.of(displayName, "artifacts"), artifacts);
        }

        public void prepareToResolveArtifacts() {
            artifacts.finalizeIfNotAlready();
        }

        @Override
        public boolean isEligibleForCaching() {
            return true;
        }

        @Override
        public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
            return artifacts.get();
        }
    }

    public static class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata, BuildableLocalConfigurationMetadata, LocalConfigurationGraphResolveMetadata {
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

        // TODO: Set these in the constructor.
        public ConfigurationInternal backingConfiguration;
        public LocalConfigurationMetadataBuilder configurationMetadataBuilder;

        private final List<LocalOriginDependencyMetadata> definedDependencies = Lists.newArrayList();
        private final List<ExcludeMetadata> definedExcludes = Lists.newArrayList();
        private final List<LocalFileDependencyMetadata> definedFiles = Lists.newArrayList();

        private ImmutableList<LocalOriginDependencyMetadata> configurationDependencies;
        private ImmutableSet<LocalFileDependencyMetadata> configurationFileDependencies;
        private ImmutableList<ExcludeMetadata> configurationExcludes;

        private final Set<LocalVariantMetadata> variants = new LinkedHashSet<>();
        private List<PublishArtifact> sourceArtifacts = Lists.newArrayList();
        private CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts;

        private final AbstractLocalComponentMetadata<?> component;

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
            ImmutableCapabilities capabilities,
            ModelContainer<?> model,
            CalculatedValueContainerFactory factory,
            AbstractLocalComponentMetadata<?> component
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

        public DefaultLocalConfigurationMetadata copy(
            Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer,
            Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts
        ) {
            DefaultLocalConfigurationMetadata copy = new DefaultLocalConfigurationMetadata(
                name,
                description,
                visible,
                transitive,
                extendsFrom,
                hierarchy,
                attributes,
                canBeConsumed,
                consumptionDeprecation,
                canBeResolved,
                capabilities,
                model,
                factory,
                component
            );

            for (LocalVariantMetadata oldVariant : variants) {
                oldVariant.prepareToResolveArtifacts();
                ImmutableList<LocalComponentArtifactMetadata> newArtifacts = copyArtifacts(oldVariant.getArtifacts(), artifactTransformer, transformedArtifacts);
                copy.variants.add(new LocalVariantMetadata(oldVariant.getName(), oldVariant.getIdentifier(), oldVariant.asDescribable(), oldVariant.getAttributes(), newArtifacts, (ImmutableCapabilities) oldVariant.getCapabilities(), factory));
            }

            realizeDependencies();
            prepareToResolveArtifacts();
            copy.definedDependencies.addAll(definedDependencies);
            copy.definedFiles.addAll(definedFiles);
            copy.definedExcludes.addAll(definedExcludes);

            ImmutableList<LocalComponentArtifactMetadata> newArtifacts = copyArtifacts(getArtifacts(), artifactTransformer, transformedArtifacts);
            copy.artifacts = factory.create(Describables.of(copy.description, "artifacts"), newArtifacts);
            copy.sourceArtifacts = null;

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
        public void enableLocking() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return asDescribable().getDisplayName();
        }

        @Override
        public DisplayName asDescribable() {
            return Describables.of(component.getId(), "configuration", name);
        }

        public ComponentResolveMetadata getComponent() {
            return component;
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
                configurationDependencies = getConfigurationsInHierarchy()
                    .flatMap(conf -> {
                        conf.realizeDependencies();
                        return conf.definedDependencies.stream();
                    })
                    .collect(ImmutableList.toImmutableList());

                AttributeValue<Category> attributeValue = this.getAttributes().findEntry(Category.CATEGORY_ATTRIBUTE);
                if (attributeValue.isPresent() && attributeValue.get().getName().equals(Category.ENFORCED_PLATFORM)) {
                    // need to wrap all dependencies to force them
                    ImmutableList.Builder<LocalOriginDependencyMetadata> forcedResult = ImmutableList.builder();
                    for (LocalOriginDependencyMetadata rawDependency : configurationDependencies) {
                        forcedResult.add(rawDependency.forced());
                    }
                    configurationDependencies = forcedResult.build();
                }
            }
            return configurationDependencies;
        }

        List<LocalOriginDependencyMetadata> getSyntheticDependencies() {
            return Collections.emptyList();
        }

        @Override
        public Set<LocalFileDependencyMetadata> getFiles() {
            if (configurationFileDependencies == null) {
                configurationFileDependencies = getConfigurationsInHierarchy()
                    .flatMap(conf -> {
                        conf.realizeDependencies();
                        return conf.definedFiles.stream();
                    })
                    .collect(ImmutableSet.toImmutableSet());
            }
            return configurationFileDependencies;
        }

        @Override
        public ImmutableList<ExcludeMetadata> getExcludes() {
            if (configurationExcludes == null) {
                configurationExcludes = getConfigurationsInHierarchy()
                    .flatMap(conf -> {
                        conf.realizeDependencies();
                        return conf.definedExcludes.stream();
                    })
                    .collect(ImmutableList.toImmutableList());
            }
            return configurationExcludes;
        }

        @Override
        public void addArtifacts(Collection<? extends PublishArtifact> artifacts) {
            sourceArtifacts.addAll(artifacts);
        }

        @Override
        public LocalConfigurationMetadata prepareToResolveArtifacts() {
            artifacts.finalizeIfNotAlready();
            for (LocalVariantMetadata variant : getVariants()) {
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
        public CapabilitiesMetadata getCapabilities() {
            return capabilities;
        }

        @Override
        public boolean isExternalVariant() {
            return false;
        }

        private Stream<DefaultLocalConfigurationMetadata> getConfigurationsInHierarchy() {
            return component.getConfigurationNames().stream().filter(hierarchy::contains).map(component::getConfiguration);
        }

        @Override
        public void addVariant(String name, VariantResolveMetadata.Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableCapabilities capabilities, Collection<? extends PublishArtifact> artifacts) {
            variants.add(new LocalVariantMetadata(name, identifier, component.getId(), displayName, attributes, artifacts, capabilities, model, factory));
        }

        synchronized void realizeDependencies() {
            if (backingConfiguration != null) {
                backingConfiguration.runDependencyActions();
                configurationMetadataBuilder.addDependenciesAndExcludes(this, backingConfiguration);
                backingConfiguration = null;
            }
        }

        @Override
        public boolean needsEvaluation() {
            return backingConfiguration != null;
        }
    }
}
