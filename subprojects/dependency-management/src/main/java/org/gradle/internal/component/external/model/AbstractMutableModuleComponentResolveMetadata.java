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
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantMetadata;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.gradle.internal.component.model.ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

abstract class AbstractMutableModuleComponentResolveMetadata<T extends DefaultConfigurationMetadata> implements MutableModuleComponentResolveMetadata, MutableComponentVariantResolveMetadata {
    public static final HashValue EMPTY_CONTENT = HashUtil.createHash("", "MD5");
    private ModuleComponentIdentifier componentId;
    private ModuleVersionIdentifier id;
    private boolean changing;
    private boolean missing;
    private String status = "integration";
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private List<? extends ModuleDependencyMetadata> dependencies;
    private HashValue contentHash = EMPTY_CONTENT;

    protected final Map<String, DependencyMetadataRules> dependencyMetadataRules = Maps.newHashMap();

    private List<MutableVariantImpl> newVariants;

    // TODO:DAZ Maybe only construct these once immutable
    private ImmutableList<? extends ComponentVariant> variants;


    protected AbstractMutableModuleComponentResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier, List<? extends ModuleDependencyMetadata> dependencies) {
        this.componentId = componentIdentifier;
        this.id = id;
        this.dependencies = dependencies;
    }

    protected AbstractMutableModuleComponentResolveMetadata(ModuleComponentResolveMetadata metadata) {
        this.componentId = metadata.getComponentId();
        this.id = metadata.getId();
        this.changing = metadata.isChanging();
        this.missing = metadata.isMissing();
        this.status = metadata.getStatus();
        this.statusScheme = metadata.getStatusScheme();
        this.moduleSource = metadata.getSource();
        this.dependencies = metadata.getDependencies();
        this.contentHash = metadata.getContentHash();
        this.variants = metadata.getVariants();
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public void setComponentId(ModuleComponentIdentifier componentId) {
        this.componentId = componentId;
        this.id = DefaultModuleVersionIdentifier.newId(componentId);
    }

    @Override
    public String getStatus() {
        return status;
    }

    protected abstract ImmutableMap<String, Configuration> getConfigurationDefinitions();

    @Override
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public List<String> getStatusScheme() {
        return statusScheme;
    }

    @Override
    public void setStatusScheme(List<String> statusScheme) {
        this.statusScheme = statusScheme;
    }

    @Override
    public boolean isMissing() {
        return missing;
    }

    @Override
    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public void setChanging(boolean changing) {
        this.changing = changing;
    }

    @Override
    public HashValue getContentHash() {
        return contentHash;
    }

    @Override
    public void setContentHash(HashValue contentHash) {
        this.contentHash = contentHash;
    }

    @Override
    public ModuleSource getSource() {
        return moduleSource;
    }

    @Override
    public void setSource(ModuleSource source) {
        this.moduleSource = source;
    }

    public void setAttributes(ImmutableAttributes attributes) {
        // map the "status" attribute to the "status" field
        // currently this is the only "attribute" that is supported
        // so this explains that we don't bother storing the whole attribute set
        // into a mutable attribute container, but only map known attributes
        // to fiels instead
        if (attributes.contains(ProjectInternal.STATUS_ATTRIBUTE)) {
            setStatus(attributes.getAttribute(ProjectInternal.STATUS_ATTRIBUTE));
        }
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getComponentId(), ivyArtifactName);
    }

    @Override
    public void addDependencyMetadataRule(String variantName, Action<DependenciesMetadata> action,
                                          Instantiator instantiator, NotationParser<Object, org.gradle.api.artifacts.DependencyMetadata> dependencyNotationParser) {
        DependencyMetadataRules rulesForVariant = dependencyMetadataRules.get(variantName);
        if (rulesForVariant == null) {
            dependencyMetadataRules.put(variantName, new DependencyMetadataRules(instantiator, dependencyNotationParser));
        }
        dependencyMetadataRules.get(variantName).addAction(action);
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        return dependencies;
    }

    public MutableComponentVariant addVariant(String variantName, ImmutableAttributes attributes) {
        MutableVariantImpl variant = new MutableVariantImpl(variantName, attributes);
        if (newVariants == null) {
            newVariants = new ArrayList<MutableVariantImpl>();
        }
        newVariants.add(variant);
        return variant;
    }

    public ImmutableList<? extends ComponentVariant> getVariants() {
        if (variants == null && newVariants == null) {
            return ImmutableList.of();
        }
        if (variants != null && newVariants == null) {
            return variants;
        }
        ImmutableList.Builder<ComponentVariant> builder = new ImmutableList.Builder<ComponentVariant>();
        if (variants != null) {
            builder.addAll(variants);
        }
        for (MutableVariantImpl variant : newVariants) {
            builder.add(new ImmutableVariantImpl(getComponentId(), variant.name, variant.attributes, ImmutableList.copyOf(variant.dependencies), ImmutableList.copyOf(variant.files)));
        }
        return builder.build();
    }

    @Override
    public boolean definesVariant(String name) {
        if (explicitlyDefinesVariants()) {
            return containsNamedVariant(name);
        } else {
            return getConfigurationDefinitions().containsKey(name);
        }
    }

    private boolean explicitlyDefinesVariants() {
        return (variants != null && !variants.isEmpty()) || (newVariants != null && !newVariants.isEmpty());
    }

    private boolean containsNamedVariant(String name) {
        if (variants != null) {
            for (ComponentVariant variant : variants) {
                if (variant.getName().equals(name)) {
                    return true;
                }
            }
        }
        if (newVariants != null) {
            for (MutableVariantImpl variant : newVariants) {
                if (variant.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }


    protected static class MutableVariantImpl implements MutableComponentVariant {
        private final String name;
        private final ImmutableAttributes attributes;
        private final List<DependencyImpl> dependencies = new ArrayList<DependencyImpl>();
        private final List<FileImpl> files = new ArrayList<FileImpl>();

        MutableVariantImpl(String name, ImmutableAttributes attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        @Override
        public void addDependency(String group, String module, VersionConstraint versionConstraint, List<Exclude> excludes) {
            dependencies.add(new DependencyImpl(group, module, versionConstraint, excludes));
        }

        @Override
        public void addFile(String name, String uri) {
            files.add(new FileImpl(name, uri));
        }

        public String getName() {
            return name;
        }
    }

    protected static class FileImpl implements ComponentVariant.File {
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

    protected static class DependencyImpl implements ComponentVariant.Dependency {
        private final String group;
        private final String module;
        private final VersionConstraint versionConstraint;
        private final ImmutableList<Exclude> excludes;

        DependencyImpl(String group, String module, VersionConstraint versionConstraint, List<Exclude> excludes) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.excludes = ImmutableList.copyOf(excludes);
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

        @Override
        public ImmutableList<Exclude> getExcludes() {
            return excludes;
        }
    }

    protected static class ImmutableVariantImpl implements ComponentVariant, VariantMetadata {
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
