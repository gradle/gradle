/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.distribution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.NamedVariantIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.repositories.AbstractResolutionAwareArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.descriptor.DistributionRepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenVariantAttributesFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ExternalModuleVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.NoOpDerivationStrategy;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A repository that resolves components from Gradle distribution modules.
 */
public class GradleDistributionRepository extends AbstractResolutionAwareArtifactRepository<RepositoryDescriptor> {

    private final InstantiatorFactory instantiatorFactory;
    private final AvailableDistributionModules availableModules;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final MavenVariantAttributesFactory mavenAttributesFactory;

    public GradleDistributionRepository(
        ObjectFactory objectFactory,
        VersionParser versionParser,
        InstantiatorFactory instantiatorFactory,
        AvailableDistributionModules availableModules,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        MavenVariantAttributesFactory mavenAttributesFactory
    ) {
        super(objectFactory, versionParser);
        this.instantiatorFactory = instantiatorFactory;
        this.availableModules = availableModules;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.mavenAttributesFactory = mavenAttributesFactory;
    }

    @Override
    protected RepositoryDescriptor createDescriptor() {
        String id = GradleVersion.current().getVersion();
        return new DistributionRepositoryDescriptor(id, getName());
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        return new DistributionModuleComponentRepository(
            getDescriptor(),
            instantiatorFactory.inject(),
            availableModules,
            moduleIdentifierFactory,
            mavenAttributesFactory
        );
    }

    private static class DistributionModuleComponentRepository implements ConfiguredModuleComponentRepository {

        private final RepositoryDescriptor descriptor;
        private final Instantiator instantiator;
        private final ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> repositoryAccess;

        public DistributionModuleComponentRepository(
            RepositoryDescriptor descriptor,
            Instantiator instantiator,
            AvailableDistributionModules availableModules,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            MavenVariantAttributesFactory mavenAttributesFactory
        ) {
            this.descriptor = descriptor;
            this.instantiator = instantiator;
            this.repositoryAccess = new DistributionRepositoryAccess(availableModules, moduleIdentifierFactory, mavenAttributesFactory);
        }

        @Override
        public boolean isDynamicResolveMode() {
            return false;
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public void setComponentResolvers(ComponentResolvers resolver) {
            // Ignore, we don't need to resolve any additional components.
        }

        @Override
        public Instantiator getComponentMetadataInstantiator() {
            return instantiator;
        }

        @Override
        public String getId() {
            return descriptor.getId();
        }

        @Override
        public String getName() {
            return descriptor.getName();
        }

        @Override
        public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getLocalAccess() {
            return repositoryAccess;
        }

        @Override
        public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getRemoteAccess() {
            return repositoryAccess;
        }

        @Override
        public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier() {
            return null;
        }

        @Override
        public boolean isContinueOnConnectionFailure() {
            return false;
        }

        @Override
        public boolean isRepositoryDisabled() {
            return false;
        }

    }

    private static class DistributionRepositoryAccess implements ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {

        private final AvailableDistributionModules availableModules;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final MavenVariantAttributesFactory mavenAttributeFactory;

        public DistributionRepositoryAccess(
            AvailableDistributionModules availableModules,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            MavenVariantAttributesFactory mavenAttributeFactory
        ) {
            this.availableModules = availableModules;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.mavenAttributeFactory = mavenAttributeFactory;
        }

        @Override
        public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, BuildableModuleVersionListingResolveResult result) {
            String version = availableModules.getModuleVersion(selector.getModuleIdentifier());
            if (version != null) {
                result.listed(Collections.singletonList(version));
            } else {
                result.failed(new ModuleVersionResolveException(selector, () -> String.format("No module available for '%s'.", selector)));
            }
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
            AvailableDistributionModules.AvailableModule module = availableModules.getModule(moduleComponentIdentifier);
            if (module == null) {
                // The distribution does not provide this dependency, or we do not expose it.
                result.missing();
                return;
            }

            if (requestMetaData.getArtifact() != null) {
                throw new UnsupportedOperationException("Cannot request explicit artifact from distribution repository.");
            }

            ModuleVersionIdentifier moduleVersionId = moduleIdentifierFactory.moduleWithVersion(moduleComponentIdentifier.getModuleIdentifier(), moduleComponentIdentifier.getVersion());
            result.resolved(new DistributionModuleComponentResolveMetadata(moduleComponentIdentifier, moduleVersionId, module, ImmutableModuleSources.of(), mavenAttributeFactory));
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            // This is a legacy resolution mechanism.
            // Distribution repositories should never support this.
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            if (!(artifact.getId() instanceof DistributionFileArtifactIdentifier)) {
                return;
            }
            result.resolved(((DistributionFileArtifactIdentifier) artifact.getId()).getFile());
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            if (availableModules.getModule(moduleComponentIdentifier) != null) {
                return MetadataFetchingCost.FAST;
            } else {
                return MetadataFetchingCost.CHEAP;
            }
        }

    }

    /**
     * Metadata for a component derived from a {@link AvailableDistributionModules.AvailableModule module} of a Gradle distribution.
     */
    private static class DistributionModuleComponentResolveMetadata implements ModuleComponentResolveMetadata {

        private final ModuleComponentIdentifier id;
        private final ModuleVersionIdentifier moduleVersionId;
        private final AvailableDistributionModules.AvailableModule module;
        private final ModuleSources sources;
        private final MavenVariantAttributesFactory mavenAttributesFactory;

        public DistributionModuleComponentResolveMetadata(
            ModuleComponentIdentifier id,
            ModuleVersionIdentifier moduleVersionId,
            AvailableDistributionModules.AvailableModule module,
            ModuleSources sources,
            MavenVariantAttributesFactory mavenAttributesFactory
        ) {
            this.id = id;
            this.moduleVersionId = moduleVersionId;
            this.module = module;
            this.sources = sources;
            this.mavenAttributesFactory = mavenAttributesFactory;
        }

        @Override
        public ModuleComponentIdentifier getId() {
            return id;
        }

        @Override
        public MutableModuleComponentResolveMetadata asMutable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModuleComponentResolveMetadata withSources(ModuleSources sources) {
            return new DistributionModuleComponentResolveMetadata(id, moduleVersionId, module, sources, mavenAttributesFactory);
        }

        @Override
        public ModuleComponentResolveMetadata withDerivationStrategy(VariantDerivationStrategy derivationStrategy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable ModuleConfigurationMetadata getConfiguration(String name) {
            return null;
        }

        @Override
        public ImmutableList<? extends ComponentVariant> getVariants() {
            return ImmutableList.of();
        }

        @Override
        public @Nullable AttributesFactory getAttributesFactory() {
            return null;
        }

        @Override
        public VariantMetadataRules getVariantMetadataRules() {
            return VariantMetadataRules.noOp();
        }

        @Override
        public VariantDerivationStrategy getVariantDerivationStrategy() {
            return NoOpDerivationStrategy.getInstance();
        }

        @Override
        public boolean isExternalVariant() {
            return false;
        }

        @Override
        public boolean isComponentMetadataRuleCachingEnabled() {
            return false;
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return moduleVersionId;
        }

        @Override
        public ModuleSources getSources() {
            return sources;
        }

        @Override
        public ImmutableAttributesSchema getAttributesSchema() {
            return ImmutableAttributesSchema.EMPTY;
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
        public @Nullable String getStatus() {
            return null;
        }

        @Override
        public @Nullable List<String> getStatusScheme() {
            return null;
        }

        @Override
        public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
            return ImmutableList.of();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public List<? extends ExternalModuleVariantGraphResolveMetadata> getVariantsForGraphTraversal() {
            return ImmutableList.of(getRuntimeVariant());
        }

        private DefaultConfigurationMetadata getRuntimeVariant() {
            List<File> classpath = module.getImplementationClasspath().getAsFiles();
            ImmutableList.Builder<ModuleComponentArtifactMetadata> artifacts = ImmutableList.builderWithExpectedSize(classpath.size());
            for (File file : classpath) {
                IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(module.getId().getModule(), "jar", "jar");
                artifacts.add(new DefaultModuleComponentArtifactMetadata(new DistributionFileArtifactIdentifier(getId(), ivyArtifactName, module.getName(), file)));
            }

            List<AvailableDistributionModules.AvailableModule> dependencyModules = module.getDependencies();
            ImmutableList.Builder<ModuleDependencyMetadata> dependencies = ImmutableList.builderWithExpectedSize(dependencyModules.size());
            for (AvailableDistributionModules.AvailableModule dependency : dependencyModules) {
                ModuleComponentIdentifier dependencyId = dependency.getId();
                dependencies.add(new GradleDependencyMetadata(
                    DefaultModuleComponentSelector.newSelector(dependencyId.getModuleIdentifier(), dependencyId.getVersion()),
                    ImmutableList.of(),
                    false,
                    false,
                    null,
                    false,
                    null
                ));
            }

            String name = "runtime";
            DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(
                name,
                new NamedVariantIdentifier(getId(), name),
                getId(),
                true,
                false,
                ImmutableSet.of(),
                artifacts.build(),
                getVariantMetadataRules(),
                ImmutableList.of(),
                mavenAttributesFactory.runtimeScope(ImmutableAttributes.EMPTY),
                false
            );
            configuration.setDependencies(dependencies.build());
            return configuration;
        }

        @Override
        public Set<String> getConfigurationNames() {
            return ImmutableSet.of();
        }

    }

    /**
     * Metadata for an artifact belonging to a distribution module.
     */
    private static class DistributionFileArtifactIdentifier extends DefaultModuleComponentArtifactIdentifier {

        private final String moduleName;
        private final File file;

        public DistributionFileArtifactIdentifier(ModuleComponentIdentifier componentId, IvyArtifactName ivyArtifactName, String moduleName, File file) {
            super(componentId, ivyArtifactName);
            this.moduleName = moduleName;
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        @Override
        public String getFileName() {
            return file.getName();
        }

        @Override
        public String getDisplayName() {
            return moduleName + " " + file.getName();
        }

    }

}
