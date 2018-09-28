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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.internal.component.model.ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

public abstract class AbstractMutableModuleComponentResolveMetadata implements MutableModuleComponentResolveMetadata {
    public static final HashValue EMPTY_CONTENT = HashUtil.createHash("", "MD5");
    private static final String DEFAULT_STATUS = "integration";

    private final ImmutableAttributesFactory attributesFactory;

    private ModuleComponentIdentifier componentId;
    private ModuleVersionIdentifier moduleVersionId;
    private boolean changing;
    private boolean missing;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private HashValue contentHash = EMPTY_CONTENT;
    private /*Mutable*/AttributeContainerInternal componentLevelAttributes;

    private final VariantMetadataRules variantMetadataRules;

    private List<MutableVariantImpl> newVariants;
    private ImmutableList<? extends ComponentVariant> variants;
    private List<ComponentIdentifier> owners;

    protected AbstractMutableModuleComponentResolveMetadata(ImmutableAttributesFactory attributesFactory, ModuleVersionIdentifier moduleVersionId, ModuleComponentIdentifier componentIdentifier) {
        this.attributesFactory = attributesFactory;
        this.componentId = componentIdentifier;
        this.moduleVersionId = moduleVersionId;
        this.componentLevelAttributes = defaultAttributes(attributesFactory);
        this.variantMetadataRules = new VariantMetadataRules(attributesFactory);
    }

    protected AbstractMutableModuleComponentResolveMetadata(ModuleComponentResolveMetadata metadata) {
        this.componentId = metadata.getId();
        this.moduleVersionId = metadata.getModuleVersionId();
        this.changing = metadata.isChanging();
        this.missing = metadata.isMissing();
        this.statusScheme = metadata.getStatusScheme();
        this.moduleSource = metadata.getSource();
        this.contentHash = metadata.getOriginalContentHash();
        this.variants = metadata.getVariants();
        this.attributesFactory = metadata.getAttributesFactory();
        this.componentLevelAttributes = attributesFactory.mutable((AttributeContainerInternal) metadata.getAttributes());
        this.variantMetadataRules = new VariantMetadataRules(attributesFactory);
        this.variantMetadataRules.setVariantDerivationStrategy(metadata.getVariantMetadataRules().getVariantDerivationStrategy());
    }

    private static AttributeContainerInternal defaultAttributes(ImmutableAttributesFactory attributesFactory) {
        return (AttributeContainerInternal) attributesFactory.mutable().attribute(ProjectInternal.STATUS_ATTRIBUTE, DEFAULT_STATUS);
    }

    @Override
    public ModuleComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionId;
    }

    @Override
    public void setId(ModuleComponentIdentifier componentId) {
        this.componentId = componentId;
        this.moduleVersionId = DefaultModuleVersionIdentifier.newId(componentId);
    }

    @Override
    public String getStatus() {
        return componentLevelAttributes.getAttribute(ProjectInternal.STATUS_ATTRIBUTE);
    }

    protected abstract ImmutableMap<String, Configuration> getConfigurationDefinitions();

    @Override
    public void setStatus(String status) {
        AttributeContainerInternal attributes = this.componentLevelAttributes;
        attributes.attribute(ProjectInternal.STATUS_ATTRIBUTE, status);
        componentLevelAttributes = attributes;
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

    public void setAttributes(AttributeContainer attributes) {
        this.componentLevelAttributes = attributesFactory.mutable((AttributeContainerInternal) attributes);
        // the "status" attribute is mandatory, so if it's missing, we need to add it
        if (!attributes.contains(ProjectInternal.STATUS_ATTRIBUTE)) {
            componentLevelAttributes.attribute(ProjectInternal.STATUS_ATTRIBUTE, DEFAULT_STATUS);
        }
    }

    @Override
    public AttributeContainer getAttributes() {
        return componentLevelAttributes;
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getModuleVersionId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getId(), ivyArtifactName);
    }

    @Override
    public VariantMetadataRules getVariantMetadataRules() {
        return variantMetadataRules;
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
            builder.add(new ImmutableVariantImpl(getId(), variant.name, variant.attributes, ImmutableList.copyOf(variant.dependencies), ImmutableList.copyOf(variant.dependencyConstraints), ImmutableList.copyOf(variant.files), ImmutableCapabilities.of(variant.capabilities)));
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

    public ImmutableAttributesFactory getAttributesFactory() {
        return attributesFactory;
    }

    @Override
    public void belongsTo(ComponentIdentifier platform) {
        if (owners == null) {
            owners = Lists.newArrayListWithExpectedSize(1);
        }
        owners.add(platform);
    }

    @Override
    public List<? extends ComponentIdentifier> getPlatformOwners() {
        return owners;
    }

    protected static class MutableVariantImpl implements MutableComponentVariant {
        private final String name;
        private final ImmutableAttributes attributes;
        private final List<DependencyImpl> dependencies = Lists.newArrayList();
        private final List<DependencyConstraintImpl> dependencyConstraints = Lists.newArrayList();
        private final List<FileImpl> files = Lists.newArrayList();
        private final List<ImmutableCapability> capabilities = Lists.newArrayList();

        MutableVariantImpl(String name, ImmutableAttributes attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        @Override
        public void addDependency(String group, String module, VersionConstraint versionConstraint, List<ExcludeMetadata> excludes, String reason, ImmutableAttributes attributes) {
            dependencies.add(new DependencyImpl(group, module, versionConstraint, excludes, reason, attributes));
        }

        @Override
        public void addDependencyConstraint(String group, String module, VersionConstraint versionConstraint, String reason, ImmutableAttributes attributes) {
            dependencyConstraints.add(new DependencyConstraintImpl(group, module, versionConstraint, reason, attributes));
        }

        @Override
        public void addCapability(String group, String name, String version) {
            capabilities.add(new ImmutableCapability(group, name, version));
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FileImpl file = (FileImpl) o;
            return Objects.equal(name, file.name)
                && Objects.equal(uri, file.uri);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name, uri);
        }
    }

    protected static class DependencyImpl implements ComponentVariant.Dependency {
        private final String group;
        private final String module;
        private final VersionConstraint versionConstraint;
        private final ImmutableList<ExcludeMetadata> excludes;
        private final String reason;
        private final ImmutableAttributes attributes;

        DependencyImpl(String group, String module, VersionConstraint versionConstraint, List<ExcludeMetadata> excludes, String reason, ImmutableAttributes attributes) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.excludes = ImmutableList.copyOf(excludes);
            this.reason = reason;
            this.attributes = attributes;
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
        public ImmutableList<ExcludeMetadata> getExcludes() {
            return excludes;
        }

        @Override
        public String getReason() {
            return reason;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DependencyImpl that = (DependencyImpl) o;
            return Objects.equal(group, that.group)
                && Objects.equal(module, that.module)
                && Objects.equal(versionConstraint, that.versionConstraint)
                && Objects.equal(excludes, that.excludes)
                && Objects.equal(reason, that.reason)
                && Objects.equal(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, module, versionConstraint, excludes, reason, attributes);
        }
    }

    protected static class DependencyConstraintImpl implements ComponentVariant.DependencyConstraint {
        private final String group;
        private final String module;
        private final VersionConstraint versionConstraint;
        private final String reason;
        private final ImmutableAttributes attributes;

        DependencyConstraintImpl(String group, String module, VersionConstraint versionConstraint, String reason, ImmutableAttributes attributes) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.reason = reason;
            this.attributes = attributes;
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
        public String getReason() {
            return reason;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DependencyConstraintImpl that = (DependencyConstraintImpl) o;
            return Objects.equal(group, that.group)
                && Objects.equal(module, that.module)
                && Objects.equal(versionConstraint, that.versionConstraint)
                && Objects.equal(reason, that.reason)
                && Objects.equal(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, module, versionConstraint, reason, attributes);
        }
    }

    protected static class ImmutableVariantImpl implements ComponentVariant, VariantResolveMetadata {
        private final ModuleComponentIdentifier componentId;
        private final String name;
        private final ImmutableAttributes attributes;
        private final ImmutableList<? extends Dependency> dependencies;
        private final ImmutableList<? extends DependencyConstraint> dependencyConstraints;
        private final ImmutableList<? extends File> files;
        private final ImmutableCapabilities capabilities;

        ImmutableVariantImpl(ModuleComponentIdentifier componentId, String name, ImmutableAttributes attributes, ImmutableList<? extends Dependency> dependencies, ImmutableList<? extends DependencyConstraint> dependencyConstraints, ImmutableList<? extends File> files, ImmutableCapabilities capabilities) {
            this.componentId = componentId;
            this.name = name;
            this.attributes = attributes;
            this.dependencies = dependencies;
            this.dependencyConstraints = dependencyConstraints;
            this.files = files;
            this.capabilities = capabilities;
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

            ImmutableVariantImpl that = (ImmutableVariantImpl) o;
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
