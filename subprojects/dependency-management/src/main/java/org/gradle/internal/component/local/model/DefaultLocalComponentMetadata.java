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
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLocalComponentMetadata implements LocalComponentMetadata, BuildableLocalComponentMetadata {
    private final Map<String, DefaultLocalConfigurationMetadata> allConfigurations = Maps.newLinkedHashMap();
    private final Multimap<String, LocalComponentArtifactMetadata> allArtifacts = ArrayListMultimap.create();
    private final Multimap<String, LocalFileDependencyMetadata> allFiles = ArrayListMultimap.create();
    private final SetMultimap<String, DefaultVariantMetadata> allVariants = LinkedHashMultimap.create();
    private final List<LocalOriginDependencyMetadata> allDependencies = Lists.newArrayList();
    private final List<Exclude> allExcludes = Lists.newArrayList();
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private List<ConfigurationMetadata> consumableConfigurations;

    public DefaultLocalComponentMetadata(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, String status, AttributesSchemaInternal attributesSchema) {
        this.id = id;
        this.componentIdentifier = componentIdentifier;
        this.status = status;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
    }

    /**
     * Creates a copy of this metadata, transforming the artifacts and dependencies of this component.
     */
    public DefaultLocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts, Transformer<LocalOriginDependencyMetadata, LocalOriginDependencyMetadata> dependencies) {
        DefaultLocalComponentMetadata copy = new DefaultLocalComponentMetadata(id, componentIdentifier, status, attributesSchema);
        for (DefaultLocalConfigurationMetadata configuration : allConfigurations.values()) {
            copy.addConfiguration(configuration.getName(), configuration.description, configuration.extendsFrom, configuration.hierarchy, configuration.visible, configuration.transitive, configuration.attributes, configuration.canBeConsumed, configuration.canBeResolved);
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
            copy.allVariants.put(entry.getKey(), new DefaultVariantMetadata(oldVariant.asDescribable(), oldVariant.getAttributes(), newArtifacts));
        }

        // Don't include file dependencies

        // Dependencies
        for (LocalOriginDependencyMetadata oldDependency : allDependencies) {
            copy.allDependencies.add(dependencies.transform(oldDependency));
        }

        // Exclude rules
        copy.allExcludes.addAll(allExcludes);

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
            LocalComponentArtifactMetadata artifactMetadata = new PublishArtifactLocalArtifactMetadata(componentIdentifier, artifact);
            addArtifact(configuration, artifactMetadata);
        }
    }

    public void addArtifact(String configuration, LocalComponentArtifactMetadata artifactMetadata) {
        allArtifacts.put(configuration, artifactMetadata);
    }

    @Override
    public void addVariant(String configuration, OutgoingVariant variant) {
        List<LocalComponentArtifactMetadata> artifacts;
        if (variant.getArtifacts().isEmpty()) {
            artifacts = ImmutableList.of();
        } else {
            ImmutableList.Builder<LocalComponentArtifactMetadata> builder = ImmutableList.builder();
            for (PublishArtifact artifact : variant.getArtifacts()) {
                builder.add(new PublishArtifactLocalArtifactMetadata(componentIdentifier, artifact));
            }
            artifacts = builder.build();
        }
        allVariants.put(configuration, new DefaultVariantMetadata(variant.asDescribable(), variant.getAttributes().asImmutable(), artifacts));
    }

    @Override
    public void addFiles(String configuration, LocalFileDependencyMetadata files) {
        allFiles.put(configuration, files);
    }

    @Override
    public void addConfiguration(String name, String description, Set<String> extendsFrom, Set<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, boolean canBeResolved) {
        assert hierarchy.contains(name);
        DefaultLocalConfigurationMetadata conf = new DefaultLocalConfigurationMetadata(name, description, visible, transitive, extendsFrom, hierarchy, attributes, canBeConsumed, canBeResolved);
        allConfigurations.put(name, conf);
    }

    @Override
    public void addDependency(LocalOriginDependencyMetadata dependency) {
        allDependencies.add(dependency);
    }

    @Override
    public void addExclude(Exclude exclude) {
        allExcludes.add(exclude);
    }

    @Override
    public String toString() {
        return componentIdentifier.getDisplayName();
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
    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public List<LocalOriginDependencyMetadata> getDependencies() {
        return allDependencies;
    }

    public List<Exclude> getExcludeRules() {
        return allExcludes;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return allConfigurations.keySet();
    }

    @Override
    public synchronized List<? extends ConfigurationMetadata> getVariantsForGraphTraversal() {
        if (consumableConfigurations == null) {
            consumableConfigurations = Lists.newArrayListWithExpectedSize(allConfigurations.size());
            for (DefaultLocalConfigurationMetadata metadata : allConfigurations.values()) {
                if (metadata.isCanBeConsumed() && !metadata.getAttributes().isEmpty()) {
                    consumableConfigurations.add(metadata);
                }
            }
        }
        return consumableConfigurations;
    }

    @Override
    public LocalConfigurationMetadata getConfiguration(final String name) {
        return allConfigurations.get(name);
    }

    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
    }

    private class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata {
        private final String name;
        private final String description;
        private final boolean transitive;
        private final boolean visible;
        private final Set<String> hierarchy;
        private final Set<String> extendsFrom;
        private final ImmutableAttributes attributes;
        private final boolean canBeConsumed;
        private final boolean canBeResolved;

        private List<LocalOriginDependencyMetadata> configurationDependencies;
        private List<LocalComponentArtifactMetadata> configurationArtifacts;
        private Set<LocalFileDependencyMetadata> configurationFileDependencies;
        private ModuleExclusion configurationExclude;

        private DefaultLocalConfigurationMetadata(String name,
                                                  String description,
                                                  boolean visible,
                                                  boolean transitive,
                                                  Set<String> extendsFrom,
                                                  Set<String> hierarchy,
                                                  ImmutableAttributes attributes,
                                                  boolean canBeConsumed,
                                                  boolean canBeResolved) {
            this.name = name;
            this.description = description;
            this.transitive = transitive;
            this.visible = visible;
            this.hierarchy = hierarchy;
            this.extendsFrom = extendsFrom;
            this.attributes = attributes;
            this.canBeConsumed = canBeConsumed;
            this.canBeResolved = canBeResolved;
        }

        @Override
        public String toString() {
            return asDescribable().getDisplayName();
        }

        @Override
        public DisplayName asDescribable() {
            return Describables.of(componentIdentifier, "configuration", name);
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
        public Collection<String> getHierarchy() {
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
        public Set<? extends VariantMetadata> getVariants() {
            return allVariants.get(name);
        }

        @Override
        public Set<LocalFileDependencyMetadata> getFiles() {
            if (configurationFileDependencies == null) {
                if (allFiles.isEmpty()) {
                    configurationFileDependencies = ImmutableSet.of();
                } else {
                    ImmutableSet.Builder<LocalFileDependencyMetadata> result = ImmutableSet.builder();
                    for (String confName : hierarchy) {
                        for (LocalFileDependencyMetadata files : allFiles.get(confName)) {
                            result.add(files);
                        }
                    }
                    configurationFileDependencies = result.build();
                }
            }
            return configurationFileDependencies;
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
                if (allDependencies.isEmpty()) {
                    configurationDependencies = ImmutableList.of();
                } else {
                    ImmutableList.Builder<LocalOriginDependencyMetadata> result = ImmutableList.builder();
                    for (LocalOriginDependencyMetadata dependency : allDependencies) {
                        if (include(dependency)) {
                            result.add(dependency);
                        }
                    }
                    configurationDependencies = result.build();
                }
            }
            return configurationDependencies;
        }

        private boolean include(LocalOriginDependencyMetadata dependency) {
            return hierarchy.contains(dependency.getModuleConfiguration());
        }

        @Override
        public ModuleExclusion getExclusions(ModuleExclusions moduleExclusions) {
            if (configurationExclude == null) {
                if (allExcludes.isEmpty()) {
                    configurationExclude = ModuleExclusions.excludeNone();
                } else {
                    ImmutableList.Builder<Exclude> filtered = ImmutableList.builder();
                    for (Exclude exclude : allExcludes) {
                        for (String config : exclude.getConfigurations()) {
                            if (hierarchy.contains(config)) {
                                filtered.add(exclude);
                                break;
                            }
                        }
                    }
                    configurationExclude = moduleExclusions.excludeAny(filtered.build());
                }
            }
            return configurationExclude;
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

            return new MissingLocalArtifactMetadata(componentIdentifier, ivyArtifactName);
        }
    }
}
