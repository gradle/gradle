/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.repo;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.model.AbstractConfigurationMetadata;
import org.gradle.internal.component.external.model.AbstractModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.language.cpp.CppBinary;
import org.gradle.nativeplatform.Linkage;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A spike implementation of a Homebrew prebuilt binary resolver. This implementation can only locate header files.
 *
 * <p>This would be refactored to separate some of thee dependency management bookkeeping from the logic that can locate header, link and runtime files,
 * to make it easier to add more resolver implementations. For now, the logic is jammed all together here.
 */
class HomebrewModuleComponentRepository implements ConfiguredModuleComponentRepository {
    private final String name;
    private final File baseDir;
    private final ImmutableAttributesFactory attributesFactory;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributes apiAttributes;
    private final NativeLibraryAttributes sharedLibraryAttributes;
    private final NativeLibraryAttributes staticLibraryAttributes;

    private class NativeLibraryAttributes {
        private final ImmutableAttributes linkAttributes;
        private final ImmutableAttributes runtimeAttributes;

        NativeLibraryAttributes(ImmutableAttributes linkAttributes, ImmutableAttributes runtimeAttributes) {
            this.linkAttributes = linkAttributes;
            this.runtimeAttributes = runtimeAttributes;
        }
    }

    public HomebrewModuleComponentRepository(String name, File baseDir, ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator, AttributesSchemaInternal schema) {
        this.name = name;
        this.baseDir = baseDir;
        this.attributesFactory = attributesFactory;
        this.schema = schema;

        // TODO - add the other attributes, eg OS, architecture
        AttributeContainerInternal attributes = attributesFactory.mutable();
        attributes.attribute(ArtifactAttributes.ARTIFACT_FORMAT, "directory");
        attributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.C_PLUS_PLUS_API));
        apiAttributes = attributes.asImmutable();

        sharedLibraryAttributes = newLibraryAttributes(attributesFactory, instantiator, Linkage.SHARED);
        staticLibraryAttributes = newLibraryAttributes(attributesFactory, instantiator, Linkage.STATIC);
    }

    private NativeLibraryAttributes newLibraryAttributes(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator, Linkage linkage) {
        AttributeContainerInternal linkAttributes = attributesFactory.mutable();
        linkAttributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.NATIVE_LINK));
        linkAttributes.attribute(CppBinary.LINKAGE_ATTRIBUTE, linkage);

        AttributeContainerInternal runtimeAttributes = attributesFactory.mutable();
        runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.NATIVE_RUNTIME));
        runtimeAttributes.attribute(CppBinary.LINKAGE_ATTRIBUTE, linkage);

        return new NativeLibraryAttributes(linkAttributes.asImmutable(), runtimeAttributes.asImmutable());
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isDynamicResolveMode() {
        return false;
    }

    @Override
    public String getId() {
        return "homebrew";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        return new EmptyRepositoryAccess();
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return new LocalRepositoryAccess(baseDir);
    }

    private static class EmptyRepositoryAccess implements ModuleComponentRepositoryAccess {
        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
        }

        @Override
        public void resolveArtifacts(ComponentResolveMetadata component, BuildableComponentArtifactsResolveResult result) {
            result.resolved(new MetadataSourcedComponentArtifacts());
        }

        @Override
        public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return MetadataFetchingCost.CHEAP;
        }
    }

    private class LocalRepositoryAccess extends EmptyRepositoryAccess {
        private final File baseDir;

        public LocalRepositoryAccess(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
            File libDir = new File(baseDir, moduleComponentIdentifier.getGroup() + "/" + moduleComponentIdentifier.getVersion());
            if (libDir.isDirectory()) {
                result.resolved(new MutablePrebuiltComponentResolveMetadata(moduleComponentIdentifier, libDir, attributesFactory, schema, apiAttributes, sharedLibraryAttributes, staticLibraryAttributes).asImmutable());
            } else {
                result.attempted(new ExternalResourceName(libDir.toURI()));
                result.missing();
            }
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            UrlBackedArtifactMetadata metadata = (UrlBackedArtifactMetadata) artifact;
            HomebrewLibraryLocation location = (HomebrewLibraryLocation) moduleSource;
            result.resolved(new File(location.libDir, metadata.getRelativeUrl()));
        }
    }

    private static class HomebrewLibraryLocation implements ModuleSource {
        private final File libDir;

        public HomebrewLibraryLocation(File libDir) {
            this.libDir = libDir;
        }
    }

    // Would refactor to share more with existing implementations
    private static class MutablePrebuiltComponentResolveMetadata extends AbstractMutableModuleComponentResolveMetadata {
        private final ImmutableAttributes apiAttributes;
        private final NativeLibraryAttributes sharedLibraryAttributes;
        private final NativeLibraryAttributes staticLibraryAttributes;

        public MutablePrebuiltComponentResolveMetadata(ModuleComponentIdentifier moduleComponentIdentifier, File libDir, ImmutableAttributesFactory attributesFactory, AttributesSchemaInternal schema, ImmutableAttributes apiAttributes, NativeLibraryAttributes sharedLibraryAttributes, NativeLibraryAttributes staticLibraryAttributes) {
            super(attributesFactory, DefaultModuleVersionIdentifier.newId(moduleComponentIdentifier), moduleComponentIdentifier, schema);
            this.apiAttributes = apiAttributes;
            this.sharedLibraryAttributes = sharedLibraryAttributes;
            this.staticLibraryAttributes = staticLibraryAttributes;
            setSource(new HomebrewLibraryLocation(libDir));
        }

        @Override
        protected ImmutableMap<String, Configuration> getConfigurationDefinitions() {
            return ImmutableMap.of();
        }

        @Override
        public ModuleComponentResolveMetadata asImmutable() {
            return new PrebuildComponentResolveMetadata(this);
        }
    }

    // Would refactor to share more with existing implementations
    private static class PrebuildComponentResolveMetadata extends AbstractModuleComponentResolveMetadata {
        private final VariantMetadataRules variantMetadataRules;
        private final ImmutableAttributes apiAttributes;
        private final NativeLibraryAttributes sharedLibraryAttributes;
        private final NativeLibraryAttributes staticLibraryAttributes;
        private final HomebrewLibraryLocation location;

        public PrebuildComponentResolveMetadata(MutablePrebuiltComponentResolveMetadata original) {
            super(original);
            apiAttributes = original.apiAttributes;
            sharedLibraryAttributes = original.sharedLibraryAttributes;
            staticLibraryAttributes = original.staticLibraryAttributes;
            variantMetadataRules = new VariantMetadataRules(getAttributesFactory(), getModuleVersionId());
            location = (HomebrewLibraryLocation)original.getSource();
        }

        public PrebuildComponentResolveMetadata(PrebuildComponentResolveMetadata original, ModuleSource source) {
            super(original, source);
            apiAttributes = original.apiAttributes;
            sharedLibraryAttributes = original.sharedLibraryAttributes;
            staticLibraryAttributes = original.staticLibraryAttributes;
            variantMetadataRules = new VariantMetadataRules(getAttributesFactory(), getModuleVersionId());
            location = original.location;
        }

        @Override
        public Set<String> getConfigurationNames() {
            return ImmutableSet.of();
        }

        @Nullable
        @Override
        public ConfigurationMetadata getConfiguration(String name) {
            return null;
        }

        @Override
        public Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
            UrlBackedArtifactMetadata includeDir = new UrlBackedArtifactMetadata(getId(), "include", "include");
            PrebuildVariant includeVariant = new PrebuildVariant("include", getId(), apiAttributes, ImmutableList.of(includeDir));

            List<PrebuildVariant> prebuildVariants = Lists.newArrayList(includeVariant);

            if (isRequestingHeaderOnly()) {
                // Using shared library attributes, it doesn't really matter
                prebuildVariants.add(new PrebuildVariant("headerOnlyLink", getId(), sharedLibraryAttributes.linkAttributes, ImmutableList.of()));
                prebuildVariants.add(new PrebuildVariant("headerOnlyRuntime", getId(), sharedLibraryAttributes.runtimeAttributes, ImmutableList.of()));
            } else {
                // Shared variant
                String sharedFileName = OperatingSystem.current().getSharedLibraryName(getId().getModule());
                String sharedFileRelativePath = "lib/" + sharedFileName;
                if (new File(location.libDir, sharedFileRelativePath).exists()) {
                    ImmutableList<UrlBackedArtifactMetadata> sharedLinkAndRuntimeFile = ImmutableList.of(new UrlBackedArtifactMetadata(getId(), sharedFileName, sharedFileRelativePath));
                    prebuildVariants.add(new PrebuildVariant("sharedLink", getId(), sharedLibraryAttributes.linkAttributes, sharedLinkAndRuntimeFile));
                    prebuildVariants.add(new PrebuildVariant("sharedRuntime", getId(), sharedLibraryAttributes.runtimeAttributes, sharedLinkAndRuntimeFile));
                }

                // Static variant
                String staticFileName = OperatingSystem.current().getStaticLibraryName(getId().getModule());
                String staticFileRelativePath = "lib/" + staticFileName;
                if (new File(location.libDir, staticFileRelativePath).exists()) {
                    ImmutableList<UrlBackedArtifactMetadata> staticLinkFile = ImmutableList.of(new UrlBackedArtifactMetadata(getId(), staticFileName, staticFileRelativePath));
                    prebuildVariants.add(new PrebuildVariant("staticLink", getId(), staticLibraryAttributes.linkAttributes, staticLinkFile));
                    prebuildVariants.add(new PrebuildVariant("staticRuntime", getId(), staticLibraryAttributes.runtimeAttributes, ImmutableList.of()));
                }
            }

            return Optional.of(ImmutableList.copyOf(prebuildVariants));
        }

        private boolean isRequestingHeaderOnly() {
            return getId().getModule().isEmpty();
        }

        @Override
        public VariantMetadataRules getVariantMetadataRules() {
            return variantMetadataRules;
        }

        @Override
        public ModuleComponentResolveMetadata withSource(ModuleSource source) {
            return new PrebuildComponentResolveMetadata(this, source);
        }

        @Override
        public MutableModuleComponentResolveMetadata asMutable() {
            throw new UnsupportedOperationException();
        }

        private static class PrebuildVariant extends AbstractConfigurationMetadata {
            public PrebuildVariant(String name, ModuleComponentIdentifier id, ImmutableAttributes attributes, ImmutableList<UrlBackedArtifactMetadata> artifacts) {
                super(id, name, true, true, artifacts, ImmutableSet.of(), ImmutableList.of(), attributes, ImmutableList.of(), ImmutableCapabilities.EMPTY);
            }

            @Override
            public List<? extends DependencyMetadata> getDependencies() {
                return ImmutableList.of();
            }
        }
    }
}
