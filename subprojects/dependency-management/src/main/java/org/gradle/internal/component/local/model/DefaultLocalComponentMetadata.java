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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLocalComponentMetadata implements LocalComponentMetadata, BuildableLocalComponentMetadata {
    private final Map<String, DefaultLocalConfigurationMetadata> allConfigurations = Maps.newHashMap();
    private final Multimap<String, LocalComponentArtifactMetadata> allArtifacts = ArrayListMultimap.create();
    private final Multimap<String, LocalFileDependencyMetadata> allFiles = ArrayListMultimap.create();
    private final SetMultimap<String, DefaultVariantMetadata> allVariants = LinkedHashMultimap.create();
    private final List<LocalOriginDependencyMetadata> allDependencies = Lists.newArrayList();
    private final List<Exclude> allExcludes = Lists.newArrayList();
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;
    private final String status;
    private final AttributesSchema attributesSchema;

    public DefaultLocalComponentMetadata(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, String status, AttributesSchema attributesSchema) {
        this.id = id;
        this.componentIdentifier = componentIdentifier;
        this.status = status;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
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
        Set<LocalComponentArtifactMetadata> artifacts;
        if (variant.getArtifacts().isEmpty()) {
            artifacts = ImmutableSet.of();
        } else {
            ImmutableSet.Builder<LocalComponentArtifactMetadata> builder = ImmutableSet.builder();
            for (PublishArtifact artifact : variant.getArtifacts()) {
                builder.add(new PublishArtifactLocalArtifactMetadata(componentIdentifier, artifact));
            }
            artifacts = builder.build();
        }
        allVariants.put(configuration, new DefaultVariantMetadata(variant.getAttributes().asImmutable(), artifacts));
    }

    @Override
    public void addFiles(String configuration, LocalFileDependencyMetadata files) {
        allFiles.put(configuration, files);
    }

    @Override
    public void addConfiguration(String name, String description, Set<String> extendsFrom, Set<String> hierarchy, boolean visible, boolean transitive, AttributeContainerInternal attributes, boolean canBeConsumed, boolean canBeResolved) {
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
    public boolean isGenerated() {
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
    public LocalConfigurationMetadata getConfiguration(final String name) {
        return allConfigurations.get(name);
    }

    @Override
    public AttributesSchema getAttributesSchema() {
        return attributesSchema;
    }

    private class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata {
        private final String name;
        private final String description;
        private final boolean transitive;
        private final boolean visible;
        private final Set<String> hierarchy;
        private final Set<String> extendsFrom;
        private final AttributeContainerInternal attributes;
        private final boolean canBeConsumed;
        private final boolean canBeResolved;

        private List<LocalOriginDependencyMetadata> configurationDependencies;
        private Set<LocalComponentArtifactMetadata> configurationArtifacts;
        private Set<LocalFileDependencyMetadata> configurationFileDependencies;
        private ModuleExclusion configurationExclude;

        private DefaultLocalConfigurationMetadata(String name,
                                                  String description,
                                                  boolean visible,
                                                  boolean transitive,
                                                  Set<String> extendsFrom,
                                                  Set<String> hierarchy,
                                                  AttributeContainerInternal attributes,
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
            return componentIdentifier.getDisplayName() + ":" + name;
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
        public Set<String> getHierarchy() {
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
        public AttributeContainerInternal getAttributes() {
            return attributes;
        }

        @Override
        public Set<? extends VariantMetadata> getVariants() {
            Set<DefaultVariantMetadata> variants = allVariants.get(name);
            if (variants.isEmpty()) {
                variants = ImmutableSet.of(new DefaultVariantMetadata(attributes, getArtifacts()));
            }
            return variants;
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
        public ModuleExclusion getExclusions() {
            if (configurationExclude == null) {
                if (allExcludes.isEmpty()) {
                    configurationExclude = ModuleExclusions.excludeNone();
                } else {
                    List<Exclude> filtered = Lists.newArrayList();
                    for (Exclude exclude : allExcludes) {
                        for (String config : exclude.getConfigurations()) {
                            if (hierarchy.contains(config)) {
                                filtered.add(exclude);
                                break;
                            }
                        }
                    }
                    configurationExclude = ModuleExclusions.excludeAny(filtered);
                }
            }
            return configurationExclude;
        }

        @Override
        public Set<? extends LocalComponentArtifactMetadata> getArtifacts() {
            if (configurationArtifacts == null) {
                if (allArtifacts.isEmpty()) {
                    configurationArtifacts = ImmutableSet.of();
                } else {
                    ImmutableSet.Builder<LocalComponentArtifactMetadata> result = ImmutableSet.builder();
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
