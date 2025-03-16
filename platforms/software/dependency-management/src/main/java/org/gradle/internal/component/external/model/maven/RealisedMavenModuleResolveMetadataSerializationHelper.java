/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.model.maven;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AbstractRealisedModuleResolveMetadataSerializationHelper;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.ForcedDependencyMetadataWrapper;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RealisedMavenModuleResolveMetadataSerializationHelper extends AbstractRealisedModuleResolveMetadataSerializationHelper {

    public RealisedMavenModuleResolveMetadataSerializationHelper(
        AttributeContainerSerializer attributeContainerSerializer,
        CapabilitySelectorSerializer capabilitySelectorSerializer,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory
    ) {
        super(attributeContainerSerializer, capabilitySelectorSerializer, moduleIdentifierFactory);
    }

    @Override
    public void writeRealisedConfigurationsData(Encoder encoder, AbstractRealisedModuleComponentResolveMetadata transformed, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        super.writeRealisedConfigurationsData(encoder, transformed, deduplicationDependencyCache);
        if (transformed instanceof RealisedMavenModuleResolveMetadata) {
            writeDerivedVariants(encoder, (RealisedMavenModuleResolveMetadata) transformed, deduplicationDependencyCache);
        }
    }

    public ModuleComponentResolveMetadata readMetadata(Decoder decoder, DefaultMavenModuleResolveMetadata resolveMetadata, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        Map<String, List<GradleDependencyMetadata>> variantToDependencies = readVariantDependencies(decoder);
        ImmutableList<? extends ComponentVariant> variants = resolveMetadata.getVariants();
        ImmutableList.Builder<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> builder = ImmutableList.builder();
        for (ComponentVariant variant : variants) {
            builder.add(new AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl(resolveMetadata.getId(), variant.getName(), variant.getAttributes().asImmutable(), variant.getDependencies(), variant.getDependencyConstraints(),
                variant.getFiles(), variant.getCapabilities(), variantToDependencies.get(variant.getName()), variant.isExternalVariant()));
        }
        ImmutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> realisedVariants = builder.build();

        Map<String, ModuleConfigurationMetadata> configurations = readMavenConfigurations(decoder, resolveMetadata, deduplicationDependencyCache);
        ImmutableList<ModuleConfigurationMetadata> derivedVariants = readDerivedVariants(decoder, resolveMetadata, deduplicationDependencyCache);

        return new RealisedMavenModuleResolveMetadata(resolveMetadata, realisedVariants, derivedVariants, configurations);
    }

    @Override
    protected void writeDependencies(Encoder encoder, ConfigurationMetadata configuration, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        List<? extends DependencyMetadata> dependencies = configuration.getDependencies();
        encoder.writeSmallInt(dependencies.size());
        for (DependencyMetadata dependency : dependencies) {
            if (dependency instanceof ForcedDependencyMetadataWrapper) {
                ForcedDependencyMetadataWrapper wrapper = (ForcedDependencyMetadataWrapper) dependency;
                dependency = wrapper.unwrap();
                if (wrapper.isForce()) {
                    encoder.writeByte(FORCED_DEPENDENCY_METADATA);
                }
            }
            if (dependency instanceof GradleDependencyMetadata) {
                encoder.writeByte(GRADLE_DEPENDENCY_METADATA);
                writeDependencyMetadata(encoder, (GradleDependencyMetadata) dependency);
            } else if (dependency instanceof MavenDependencyMetadata) {
                MavenDependencyMetadata dependencyMetadata = (MavenDependencyMetadata) dependency;
                MavenDependencyDescriptor dependencyDescriptor = dependencyMetadata.getDependencyDescriptor();
                encoder.writeByte(MAVEN_DEPENDENCY_METADATA);
                writeMavenDependency(encoder, dependencyDescriptor, deduplicationDependencyCache);
                encoder.writeNullableString(dependency.getReason());
            } else {
                throw new IllegalStateException("Unknown type of dependency: " + dependency.getClass());
            }
        }
    }

    private void writeDerivedVariants(Encoder encoder, RealisedMavenModuleResolveMetadata metadata, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        ImmutableList<? extends ConfigurationMetadata> derivedVariants = metadata.getDerivedVariants();
        encoder.writeSmallInt(derivedVariants.size());
        for (ConfigurationMetadata derivedVariant : derivedVariants) {
            writeConfiguration(encoder, derivedVariant);
            writeFiles(encoder, derivedVariant.getArtifacts());
            writeDerivedVariantExtra(encoder, derivedVariant, deduplicationDependencyCache);
        }
    }

    private void writeDerivedVariantExtra(Encoder encoder, ConfigurationMetadata derivedVariant, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        encoder.writeBoolean(derivedVariant.isTransitive());
        encoder.writeBoolean(derivedVariant.isVisible());
        writeStringSet(encoder, derivedVariant.getHierarchy());
        writeMavenExcludeRules(encoder, derivedVariant.getExcludes());
        writeDependencies(encoder, derivedVariant, deduplicationDependencyCache);
    }

    private Map<String, ModuleConfigurationMetadata> readMavenConfigurations(Decoder decoder, DefaultMavenModuleResolveMetadata metadata, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();

        int configurationsCount = decoder.readSmallInt();
        Map<String, ModuleConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(configurationsCount);
        for (int i = 0; i < configurationsCount; i++) {
            String configurationName = decoder.readString();
            Configuration configuration = configurationDefinitions.get(configurationName);
            ImmutableSet<String> hierarchy = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration, configurationDefinitions);
            ImmutableAttributes attributes = getAttributeContainerSerializer().read(decoder);
            ImmutableCapabilities capabilities = readCapabilities(decoder);
            boolean isExternalVariant = decoder.readBoolean();
            ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = readFiles(decoder, metadata.getId());

            RealisedConfigurationMetadata configurationMetadata = new RealisedConfigurationMetadata(metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(),
                hierarchy, artifacts, ImmutableList.of(), attributes, capabilities, false, isExternalVariant);
            ImmutableList<ModuleDependencyMetadata> dependencies = readDependencies(decoder, deduplicationDependencyCache);
            configurationMetadata.setDependencies(dependencies);
            configurations.put(configurationName, configurationMetadata);
        }
        return configurations;
    }

    private ImmutableList<ModuleDependencyMetadata> readDependencies(Decoder decoder, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        ImmutableList.Builder<ModuleDependencyMetadata> builder = ImmutableList.builder();
        int dependenciesCount = decoder.readSmallInt();
        if (dependenciesCount == 0) {
            return ImmutableList.of();
        }
        for (int j = 0; j < dependenciesCount; j++) {
            byte dependencyType = decoder.readByte();
            boolean force = false;
            if (dependencyType == FORCED_DEPENDENCY_METADATA) {
                force = true;
                dependencyType = decoder.readByte();
            }
            ModuleDependencyMetadata md;
            switch (dependencyType) {
                case GRADLE_DEPENDENCY_METADATA:
                    md = readDependencyMetadata(decoder);
                    break;
                case MAVEN_DEPENDENCY_METADATA:
                    MavenDependencyDescriptor mavenDependencyDescriptor = readMavenDependency(decoder, deduplicationDependencyCache);
                    String reason = decoder.readNullableString();
                    md = new MavenDependencyMetadata(mavenDependencyDescriptor, reason, false);
                    break;
                case IVY_DEPENDENCY_METADATA:
                    throw new IllegalStateException("Unexpected Ivy dependency for Maven module");
                default:
                    throw new IllegalStateException("Unknown dependency type " + dependencyType);
            }
            if (force) {
                md = new ForcedDependencyMetadataWrapper(md);
            }
            builder.add(md);
        }
        return builder.build();
    }

    private ImmutableList<ModuleConfigurationMetadata> readDerivedVariants(Decoder decoder, DefaultMavenModuleResolveMetadata resolveMetadata, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        int derivedVariantsCount = decoder.readSmallInt();
        if (derivedVariantsCount == 0) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ModuleConfigurationMetadata> builder = new ImmutableList.Builder<>();
        for (int i = 0; i < derivedVariantsCount; i++) {
            builder.add(readDerivedVariant(decoder, resolveMetadata, deduplicationDependencyCache));
        }
        return builder.build();
    }

    private ModuleConfigurationMetadata readDerivedVariant(Decoder decoder, DefaultMavenModuleResolveMetadata resolveMetadata, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        String name = decoder.readString();
        ImmutableAttributes attributes = attributeContainerSerializer.read(decoder);
        ImmutableCapabilities immutableCapabilities = readCapabilities(decoder);
        boolean isExternalVariant = decoder.readBoolean();
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = readFiles(decoder, resolveMetadata.getId());
        boolean transitive = decoder.readBoolean();
        boolean visible = decoder.readBoolean();
        ImmutableSet<String> hierarchy = ImmutableSet.copyOf(readStringSet(decoder));
        List<ExcludeMetadata> excludeMetadata = readMavenExcludes(decoder);
        RealisedConfigurationMetadata realized = new RealisedConfigurationMetadata(
            resolveMetadata.getId(),
            name,
            transitive,
            visible,
            hierarchy,
            artifacts,
            ImmutableList.copyOf(excludeMetadata),
            attributes,
            immutableCapabilities,
            false,
            isExternalVariant
        );
        ImmutableList<ModuleDependencyMetadata> dependencies = readDependencies(decoder, deduplicationDependencyCache);
        realized.setDependencies(dependencies);
        return realized;

    }

    private MavenDependencyDescriptor readMavenDependency(Decoder decoder, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        int mapping = decoder.readSmallInt();
        if (mapping == deduplicationDependencyCache.size()) {
            ModuleComponentSelector requested = getComponentSelectorSerializer().read(decoder);
            IvyArtifactName artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder);
            List<ExcludeMetadata> mavenExcludes = readMavenExcludes(decoder);
            MavenScope scope = MavenScope.values()[decoder.readSmallInt()];
            MavenDependencyType type = MavenDependencyType.values()[decoder.readSmallInt()];
            MavenDependencyDescriptor mavenDependencyDescriptor = new MavenDependencyDescriptor(scope, type, requested, artifactName, mavenExcludes);
            deduplicationDependencyCache.put(mapping, mavenDependencyDescriptor);
            return mavenDependencyDescriptor;
        } else {
            MavenDependencyDescriptor mavenDependencyDescriptor = deduplicationDependencyCache.get(mapping);
            assert mavenDependencyDescriptor != null;
            return mavenDependencyDescriptor;
        }
    }

    private void writeMavenDependency(Encoder encoder, MavenDependencyDescriptor mavenDependency, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        int nextMapping = deduplicationDependencyCache.size();
        Integer mapping = deduplicationDependencyCache.putIfAbsent(mavenDependency, nextMapping);
        if (mapping != null) {
            encoder.writeSmallInt(mapping);
        } else {
            encoder.writeSmallInt(nextMapping);
            getComponentSelectorSerializer().write(encoder, mavenDependency.getSelector());
            IvyArtifactNameSerializer.INSTANCE.writeNullable(encoder, mavenDependency.getDependencyArtifact());
            writeMavenExcludeRules(encoder, mavenDependency.getAllExcludes());
            encoder.writeSmallInt(mavenDependency.getScope().ordinal());
            encoder.writeSmallInt(mavenDependency.getType().ordinal());
        }
    }

}
