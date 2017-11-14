/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.VariantMetadata;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION;

public class DefaultMutableIvyModuleResolveMetadata extends AbstractMutableModuleComponentResolveMetadata<IvyConfigurationMetadata> implements MutableIvyModuleResolveMetadata {
    private final ImmutableList<Artifact> artifactDefinitions;
    private Map<Artifact, ModuleComponentArtifactMetadata> artifacts;
    private final ImmutableMap<String, Configuration> configurations;
    private ImmutableList<Exclude> excludes;
    private ImmutableMap<NamespaceId, String> extraAttributes;
    private String branch;

    private final Map<String, MutableVariantImpl> mutableVariants = Maps.newHashMap();
    private ImmutableList<? extends ConfigurationMetadata> graphVariants;

    /**
     * Creates default metadata for an Ivy module version with no ivy.xml descriptor.
     */
    public static DefaultMutableIvyModuleResolveMetadata missing(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier) {
        DefaultMutableIvyModuleResolveMetadata metadata = new DefaultMutableIvyModuleResolveMetadata(id, componentIdentifier);
        metadata.setMissing(true);
        return metadata;
    }

    public DefaultMutableIvyModuleResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier) {
        this(id, componentIdentifier,
            ImmutableList.of(new Configuration(DEFAULT_CONFIGURATION, true, true, ImmutableSet.<String>of())),
            ImmutableList.<ModuleDependencyMetadata>of(),
            ImmutableList.of(new Artifact(new DefaultIvyArtifactName(componentIdentifier.getModule(), "jar", "jar"), ImmutableSet.of(DEFAULT_CONFIGURATION))));
    }

    public DefaultMutableIvyModuleResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier, Collection<Configuration> configurations, Collection<? extends ModuleDependencyMetadata> dependencies, Collection<? extends Artifact> artifacts) {
        super(id, componentIdentifier, ImmutableList.copyOf(dependencies));
        this.configurations = toMap(configurations);
        this.artifactDefinitions = ImmutableList.copyOf(artifacts);
        this.excludes = ImmutableList.of();
        this.extraAttributes = ImmutableMap.of();
    }

    public DefaultMutableIvyModuleResolveMetadata(IvyModuleResolveMetadata metadata) {
        super(metadata);
        this.configurations = metadata.getConfigurationDefinitions();
        this.artifactDefinitions = metadata.getArtifactDefinitions();
        this.excludes = metadata.getExcludes();
        this.branch = metadata.getBranch();
        this.extraAttributes = metadata.getExtraAttributes();
    }

    private static ImmutableMap<String, Configuration> toMap(Collection<Configuration> configurations) {
        ImmutableMap.Builder<String, Configuration> builder = ImmutableMap.builder();
        for (Configuration configuration : configurations) {
            builder.put(configuration.getName(), configuration);
        }
        return builder.build();
    }

    @Override
    protected IvyConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<IvyConfigurationMetadata> parents, ImmutableList<? extends ModuleComponentArtifactMetadata> artifactOverrides) {
        Set<ModuleComponentArtifactMetadata> artifacts = new LinkedHashSet<ModuleComponentArtifactMetadata>();
        collectArtifactsFor(name, artifactOverrides, artifacts);
        for (IvyConfigurationMetadata parent : parents) {
            artifacts.addAll(parent.getArtifacts());
        }

        return new IvyConfigurationMetadata(componentId, name, transitive, visible, parents, excludes, ImmutableList.copyOf(artifacts));
    }

    private void collectArtifactsFor(String name, Collection<? extends ModuleComponentArtifactMetadata> artifactOverrides, Collection<ModuleComponentArtifactMetadata> dest) {
        if (artifactOverrides != null) {
            dest.addAll(artifactOverrides);
            return;
        }
        if (artifacts == null) {
            artifacts = new IdentityHashMap<Artifact, ModuleComponentArtifactMetadata>();
        }
        for (Artifact artifact : artifactDefinitions) {
            if (artifact.getConfigurations().contains(name)) {
                ModuleComponentArtifactMetadata artifactMetadata = artifacts.get(artifact);
                if (artifactMetadata == null) {
                    artifactMetadata = new DefaultModuleComponentArtifactMetadata(getComponentId(), artifact.getArtifactName());
                    artifacts.put(artifact, artifactMetadata);
                }
                dest.add(artifactMetadata);
            }
        }
    }

    @Override
    public ImmutableMap<String, Configuration> getConfigurationDefinitions() {
        return configurations;
    }

    @Override
    public boolean definesVariant(String name) {
        return configurations.containsKey(name);
    }

    @Override
    public ImmutableList<Artifact> getArtifactDefinitions() {
        return artifactDefinitions;
    }

    @Override
    public ImmutableList<Exclude> getExcludes() {
        return excludes;
    }

    @Override
    public void setExcludes(Iterable<? extends Exclude> excludes) {
        this.excludes = ImmutableList.copyOf(excludes);
        resetConfigurations();
    }

    @Override
    public ImmutableMap<NamespaceId, String> getExtraAttributes() {
        return extraAttributes;
    }

    @Override
    public void setExtraAttributes(Map<NamespaceId, String> extraAttributes) {
        this.extraAttributes = ImmutableMap.copyOf(extraAttributes);
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public IvyModuleResolveMetadata asImmutable() {
        return new DefaultIvyModuleResolveMetadata(this);
    }

    @Override
    public MutableComponentVariant addVariant(final String variantName, ImmutableAttributes attributes) {
        MutableVariantImpl variant = new MutableVariantImpl(variantName, attributes);
        mutableVariants.put(variantName, variant);
        return variant;
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        ImmutableList.Builder<ComponentVariant> builder = new ImmutableList.Builder<ComponentVariant>();
        for (MutableVariantImpl variant : mutableVariants.values()) {
            builder.add(new ImmutableVariantImpl(getComponentId(), variant.name, variant.attributes, ImmutableList.copyOf(variant.dependencies), ImmutableList.copyOf(variant.files)));
        }
        return builder.build();
    }

    @Override
    public ImmutableList<? extends ConfigurationMetadata> getVariantsForTraversal() {
        if (graphVariants == null) {
            ImmutableList<? extends ComponentVariant> variants = getVariants();
            if (variants.isEmpty()) {
                graphVariants = ImmutableList.of();
            } else {
                List<VariantBackedConfigurationMetadata> configurations = new ArrayList<VariantBackedConfigurationMetadata>(variants.size());
                for (ComponentVariant variant : variants) {
                    configurations.add(new VariantBackedConfigurationMetadata(getComponentId(), variant, dependencyMetadataRules.get(variant.getName())));
                }
                graphVariants = ImmutableList.copyOf(configurations);
            }
        }
        return graphVariants;
    }

    private static class MutableVariantImpl implements MutableComponentVariant {
        private final String name;
        private final ImmutableAttributes attributes;
        private final List<DependencyImpl> dependencies = new ArrayList<DependencyImpl>();
        private final List<FileImpl> files = new ArrayList<FileImpl>();

        MutableVariantImpl(String name, ImmutableAttributes attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        @Override
        public void addDependency(String group, String module, VersionConstraint versionConstraint) {
            dependencies.add(new DependencyImpl(group, module, versionConstraint));
        }

        @Override
        public void addFile(String name, String uri) {
            files.add(new FileImpl(name, uri));
        }
    }

    private static class FileImpl implements ComponentVariant.File {
        private final String name;
        private final String uri;

        FileImpl(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUri() {
            return uri;
        }
    }

    private static class DependencyImpl implements ComponentVariant.Dependency {
        private final String group;
        private final String module;
        private final VersionConstraint versionConstraint;

        DependencyImpl(String group, String module, VersionConstraint versionConstraint) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
        }

        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public String getModule() {
            return module;
        }

        @Override
        public VersionConstraint getVersionConstraint() {
            return versionConstraint;
        }
    }

    private static class ImmutableVariantImpl implements ComponentVariant, VariantMetadata {
        private final ModuleComponentIdentifier componentId;
        private final String name;
        private final ImmutableAttributes attributes;
        private final ImmutableList<DependencyImpl> dependencies;
        private final ImmutableList<FileImpl> files;

        ImmutableVariantImpl(ModuleComponentIdentifier componentId, String name, ImmutableAttributes attributes, ImmutableList<DependencyImpl> dependencies, ImmutableList<FileImpl> files) {
            this.componentId = componentId;
            this.name = name;
            this.attributes = attributes;
            this.dependencies = dependencies;
            this.files = files;
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
        public ImmutableList<? extends File> getFiles() {
            return files;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            List<ComponentArtifactMetadata> artifacts = new ArrayList<ComponentArtifactMetadata>(files.size());
            for (ComponentVariant.File file : files) {
                artifacts.add(new UrlBackedArtifactMetadata(componentId, file.getName(), file.getUri()));
            }
            return artifacts;
        }

    }
}
