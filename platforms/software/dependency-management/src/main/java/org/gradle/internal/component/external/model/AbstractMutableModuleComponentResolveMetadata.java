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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.MutableModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.internal.component.external.model.ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

public abstract class AbstractMutableModuleComponentResolveMetadata implements MutableModuleComponentResolveMetadata {
    private static final String DEFAULT_STATUS = "integration";

    private final ImmutableAttributesFactory attributesFactory;

    private ModuleComponentIdentifier componentId;
    private ModuleVersionIdentifier moduleVersionId;
    private boolean changing;
    private boolean missing;
    private boolean externalVariant;
    private boolean isComponentMetadataRuleCachingEnabled;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private MutableModuleSources moduleSources;
    private /*Mutable*/AttributeContainerInternal componentLevelAttributes;
    private final AttributesSchemaInternal schema;

    private final VariantMetadataRules variantMetadataRules;
    private final VariantDerivationStrategy variantDerivationStrategy;

    private List<MutableComponentVariant> newVariants;
    private ImmutableList<? extends ComponentVariant> variants;
    private Set<VirtualComponentIdentifier> owners;

    protected AbstractMutableModuleComponentResolveMetadata(ImmutableAttributesFactory attributesFactory,
                                                            ModuleVersionIdentifier moduleVersionId,
                                                            ModuleComponentIdentifier componentIdentifier,
                                                            AttributesSchemaInternal schema) {
        this.attributesFactory = attributesFactory;
        this.componentId = componentIdentifier;
        this.moduleVersionId = moduleVersionId;
        this.componentLevelAttributes = defaultAttributes(attributesFactory);
        this.schema = schema;
        this.variantMetadataRules = new VariantMetadataRules(attributesFactory, moduleVersionId);
        this.moduleSources = new MutableModuleSources();
        this.variantDerivationStrategy = NoOpDerivationStrategy.getInstance();
        this.isComponentMetadataRuleCachingEnabled = true;
    }

    protected AbstractMutableModuleComponentResolveMetadata(ModuleComponentResolveMetadata metadata) {
        this.componentId = metadata.getId();
        this.moduleVersionId = metadata.getModuleVersionId();
        this.changing = metadata.isChanging();
        this.missing = metadata.isMissing();
        this.statusScheme = metadata.getStatusScheme();
        this.moduleSources = MutableModuleSources.of(metadata.getSources());
        this.variants = metadata.getVariants();
        this.attributesFactory = metadata.getAttributesFactory();
        this.schema = metadata.getAttributesSchema();
        this.componentLevelAttributes = attributesFactory.mutable(metadata.getAttributes());
        this.variantDerivationStrategy = metadata.getVariantDerivationStrategy();
        this.variantMetadataRules = new VariantMetadataRules(attributesFactory, moduleVersionId);
        this.externalVariant = metadata.isExternalVariant();
        this.isComponentMetadataRuleCachingEnabled = metadata.isComponentMetadataRuleCachingEnabled();
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

    public VariantDerivationStrategy getVariantDerivationStrategy() {
        return variantDerivationStrategy;
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
    public boolean isExternalVariant() {
        return externalVariant;
    }

    @Override
    public void setExternalVariant(boolean externalVariant) {
        this.externalVariant = externalVariant;
    }

    @Override
    public boolean isComponentMetadataRuleCachingEnabled() {
        return isComponentMetadataRuleCachingEnabled;
    }

    @Override
    public void setComponentMetadataRuleCachingEnabled(boolean componentMetadataRuleCachingEnabled) {
        this.isComponentMetadataRuleCachingEnabled = componentMetadataRuleCachingEnabled;
    }

    @Override
    public MutableModuleSources getSources() {
        return moduleSources;
    }

    @Override
    public void setSources(ModuleSources sources) {
        this.moduleSources = MutableModuleSources.of(sources);
    }

    @Override
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

    @Override
    public MutableComponentVariant addVariant(String variantName, ImmutableAttributes attributes) {
        return addVariant(new MutableVariantImpl(variantName, attributes));
    }

    @Override
    public MutableComponentVariant addVariant(MutableComponentVariant variant) {
        if (newVariants == null) {
            newVariants = new ArrayList<>();
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
        ImmutableList.Builder<ComponentVariant> builder = new ImmutableList.Builder<>();
        if (variants != null) {
            builder.addAll(variants);
        }
        for (MutableComponentVariant variant : newVariants) {
            builder.add(new ImmutableVariantImpl(getId(), variant.getName(), variant.getAttributes(), ImmutableList.copyOf(variant.getDependencies()), ImmutableList.copyOf(variant.getDependencyConstraints()), ImmutableList.copyOf(variant.getFiles()), ImmutableCapabilities.of(variant.getCapabilities()), variant.isAvailableExternally()));
        }
        return builder.build();
    }

    @Override
    public List<? extends MutableComponentVariant> getMutableVariants() {
        return newVariants;
    }

    @Override
    public ImmutableAttributesFactory getAttributesFactory() {
        return attributesFactory;
    }

    public AttributesSchemaInternal getAttributesSchema() {
        return schema;
    }

    @Override
    public void belongsTo(VirtualComponentIdentifier platform) {
        if (owners == null) {
            owners = new LinkedHashSet<>();
        }
        owners.add(platform);
    }

    @Override
    public Set<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return owners;
    }

    protected static class MutableVariantImpl implements MutableComponentVariant {
        private final String name;
        private final List<ComponentVariant.Dependency> dependencies = new ArrayList<>();
        private final List<ComponentVariant.DependencyConstraint> dependencyConstraints = new ArrayList<>();
        private final List<FileImpl> files = new ArrayList<>();
        private final Set<Capability> capabilities = new LinkedHashSet<>();
        private boolean availableExternally;

        private ImmutableAttributes attributes;

        MutableVariantImpl(String name, ImmutableAttributes attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        @Override
        public List<ComponentVariant.Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public List<ComponentVariant.DependencyConstraint> getDependencyConstraints() {
            return dependencyConstraints;
        }

        @Override
        public Set<Capability> getCapabilities() {
            return capabilities;
        }

        @Override
        public void addDependency(String group, String module, VersionConstraint versionConstraint, List<ExcludeMetadata> excludes, String reason, ImmutableAttributes attributes, List<? extends Capability> requestedCapabilities, boolean endorsing, @Nullable IvyArtifactName artifact) {
            dependencies.add(new DependencyImpl(group, module, versionConstraint, excludes, reason, attributes, requestedCapabilities, endorsing, artifact));
        }

        @Override
        public void addDependencyConstraint(String group, String module, VersionConstraint versionConstraint, String reason, ImmutableAttributes attributes) {
            dependencyConstraints.add(new DependencyConstraintImpl(group, module, versionConstraint, reason, attributes));
        }

        @Override
        public void addCapability(String group, String name, String version) {
            capabilities.add(new DefaultImmutableCapability(group, name, version));
        }

        @Override
        public void addCapability(Capability capability) {
            capabilities.add(capability);
        }

        @Override
        public List<? extends ComponentVariant.File> getFiles() {
            return files;
        }

        @Override
        public boolean removeFile(ComponentVariant.File file) {
            return files.remove(file);
        }

        @Override
        public void addFile(String name, String uri) {
            files.add(new FileImpl(name, uri));
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return attributes;
        }

        @Override
        public void setAttributes(ImmutableAttributes updatedAttributes) {
            this.attributes = updatedAttributes;
        }

        @Override
        public MutableComponentVariant copy(String variantName, ImmutableAttributes attributes, Capability capability) {
            MutableVariantImpl copy = new MutableVariantImpl(variantName, attributes);
            copy.dependencies.addAll(this.dependencies);
            copy.dependencyConstraints.addAll(this.dependencyConstraints);
            copy.files.addAll(this.files);
            copy.capabilities.add(capability);
            copy.availableExternally = this.availableExternally;
            return copy;
        }

        @Override
        public boolean isAvailableExternally() {
            return availableExternally;
        }

        @Override
        public void setAvailableExternally(boolean availableExternally) {
            this.availableExternally = availableExternally;
        }
    }

    public static class FileImpl implements ComponentVariant.File {
        private final String name;
        private final String uri;

        public FileImpl(String name, String uri) {
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
        private final ImmutableList<Capability> requestedCapabilities;
        private final boolean endorsing;
        private final IvyArtifactName dependencyArtifact;

        DependencyImpl(String group,
                       String module,
                       VersionConstraint versionConstraint,
                       List<ExcludeMetadata> excludes,
                       String reason,
                       ImmutableAttributes attributes,
                       List<? extends Capability> requestedCapabilities,
                       boolean endorsing,
                       @Nullable IvyArtifactName dependencyArtifact) {
            this.group = group;
            this.module = module;
            this.versionConstraint = versionConstraint;
            this.excludes = ImmutableList.copyOf(excludes);
            this.reason = reason;
            this.attributes = attributes;
            this.requestedCapabilities = ImmutableList.copyOf(
                requestedCapabilities.stream()
                    .map(c -> new DefaultImmutableCapability(c.getGroup(), c.getName(), c.getVersion()))
                    .collect(Collectors.toList())
            );
            this.endorsing = endorsing;
            this.dependencyArtifact = dependencyArtifact;
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
        public List<Capability> getRequestedCapabilities() {
            return requestedCapabilities;
        }

        @Override
        public boolean isEndorsingStrictVersions() {
            return endorsing;
        }

        @Override
        @Nullable
        public IvyArtifactName getDependencyArtifact() {
            return dependencyArtifact;
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
                && Objects.equal(attributes, that.attributes)
                && Objects.equal(requestedCapabilities, that.requestedCapabilities)
                && Objects.equal(endorsing, that.endorsing)
                && Objects.equal(dependencyArtifact, that.dependencyArtifact);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, module, versionConstraint, excludes, reason, attributes, endorsing, dependencyArtifact);
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
        private final boolean externalVariant;

        ImmutableVariantImpl(ModuleComponentIdentifier componentId,
                             String name,
                             ImmutableAttributes attributes,
                             ImmutableList<? extends Dependency> dependencies,
                             ImmutableList<? extends DependencyConstraint> dependencyConstraints,
                             ImmutableList<? extends File> files,
                             ImmutableCapabilities capabilities,
                             boolean externalVariant) {
            this.componentId = componentId;
            this.name = name;
            this.attributes = attributes;
            this.dependencies = dependencies;
            this.dependencyConstraints = dependencyConstraints;
            this.files = files;
            this.capabilities = capabilities;
            this.externalVariant = externalVariant;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Identifier getIdentifier() {
            return new ComponentConfigurationIdentifier(componentId, name);
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
        public ImmutableCapabilities getCapabilities() {
            return capabilities;
        }

        @Override
        public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
            ImmutableList.Builder<ComponentArtifactMetadata> artifacts = new ImmutableList.Builder<>();
            for (ComponentVariant.File file : files) {
                artifacts.add(new UrlBackedArtifactMetadata(componentId, file.getName(), file.getUri()));
            }
            return artifacts.build();
        }

        @Override
        public boolean isExternalVariant() {
            return externalVariant;
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
                && Objects.equal(files, that.files)
                && externalVariant == that.externalVariant;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentId,
                name,
                attributes,
                dependencies,
                dependencyConstraints,
                files,
                externalVariant);
        }
    }

}
