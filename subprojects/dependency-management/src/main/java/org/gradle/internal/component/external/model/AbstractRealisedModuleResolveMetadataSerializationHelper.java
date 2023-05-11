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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractRealisedModuleResolveMetadataSerializationHelper {

    protected static final byte GRADLE_DEPENDENCY_METADATA = 1;
    protected static final byte MAVEN_DEPENDENCY_METADATA = 2;
    protected static final byte IVY_DEPENDENCY_METADATA = 3;
    protected static final byte FORCED_DEPENDENCY_METADATA = 4;
    protected final AttributeContainerSerializer attributeContainerSerializer;
    private final ModuleComponentSelectorSerializer componentSelectorSerializer;
    private final ExcludeRuleConverter excludeRuleConverter;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public AbstractRealisedModuleResolveMetadataSerializationHelper(AttributeContainerSerializer attributeContainerSerializer, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.componentSelectorSerializer = new ModuleComponentSelectorSerializer(attributeContainerSerializer);
        this.excludeRuleConverter = new DefaultExcludeRuleConverter(moduleIdentifierFactory);
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    protected AttributeContainerSerializer getAttributeContainerSerializer() {
        return attributeContainerSerializer;
    }

    protected ModuleComponentSelectorSerializer getComponentSelectorSerializer() {
        return componentSelectorSerializer;
    }

    public void writeRealisedVariantsData(Encoder encoder, AbstractRealisedModuleComponentResolveMetadata transformed) throws IOException {
        encoder.writeSmallInt(transformed.getVariants().size());
        for (ComponentVariant variant: transformed.getVariants()) {
            if (variant instanceof AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl) {
                AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl realisedVariant = (AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl) variant;
                encoder.writeString(realisedVariant.getName());
                encoder.writeSmallInt(realisedVariant.getDependencyMetadata().size());
                for (ModuleDependencyMetadata dependencyMetadata: realisedVariant.getDependencyMetadata()) {
                    if (dependencyMetadata instanceof GradleDependencyMetadata) {
                        writeDependencyMetadata(encoder, (GradleDependencyMetadata) dependencyMetadata);
                    }
                }
            } else {
                throw new IllegalStateException("Unknown type of variant: " + variant.getClass());
            }
        }
    }

    public void writeRealisedConfigurationsData(Encoder encoder, AbstractRealisedModuleComponentResolveMetadata transformed, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        encoder.writeSmallInt(transformed.getConfigurationNames().size());
        for (String configurationName: transformed.getConfigurationNames()) {
            ConfigurationMetadata configuration = transformed.getConfiguration(configurationName);
            writeConfiguration(encoder, configuration);
            writeFiles(encoder, configuration.getArtifacts());
            writeDependencies(encoder, configuration, deduplicationDependencyCache);
        }
    }

    protected void writeConfiguration(Encoder encoder, ConfigurationMetadata configuration) throws IOException {
        assert configuration != null;
        encoder.writeString(configuration.getName());
        attributeContainerSerializer.write(encoder, configuration.getAttributes());
        writeCapabilities(encoder, configuration.getCapabilities().getCapabilities());
        encoder.writeBoolean(configuration.isExternalVariant());
    }

    protected Map<String, List<GradleDependencyMetadata>> readVariantDependencies(Decoder decoder) throws IOException {
        int variantsCount = decoder.readSmallInt();
        Map<String, List<GradleDependencyMetadata>> variantsToDependencies = Maps.newHashMapWithExpectedSize(variantsCount);
        for (int i = 0; i < variantsCount; i++) {
            String variantName = decoder.readString();
            int dependencyCount = decoder.readSmallInt();
            List<GradleDependencyMetadata> dependencies = Lists.newArrayListWithExpectedSize(dependencyCount);
            for (int j = 0; j < dependencyCount; j++) {
                dependencies.add(readDependencyMetadata(decoder));
            }
            variantsToDependencies.put(variantName, dependencies);
        }
        return variantsToDependencies;
    }

    protected GradleDependencyMetadata readDependencyMetadata(Decoder decoder) throws IOException {
        ModuleComponentSelector selector = componentSelectorSerializer.read(decoder);
        List<ExcludeMetadata> excludes = readMavenExcludes(decoder);
        boolean constraint = decoder.readBoolean();
        boolean endorsing = decoder.readBoolean();
        boolean force = decoder.readBoolean();
        String reason = decoder.readNullableString();
        IvyArtifactName artifact = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder);
        return new GradleDependencyMetadata(selector, excludes, constraint, endorsing, reason, force, artifact);
    }

    protected ImmutableList<? extends ModuleComponentArtifactMetadata> readFiles(Decoder decoder, ModuleComponentIdentifier componentIdentifier) throws IOException {
        ImmutableList.Builder<ModuleComponentArtifactMetadata> artifacts = new ImmutableList.Builder<>();
        int artifactsCount = decoder.readSmallInt();
        for (int i = 0; i < artifactsCount; i++) {
            IvyArtifactName artifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder);
            String timestamp = decoder.readNullableString();

            String version;
            ModuleComponentIdentifier cid = componentIdentifier;
            if (timestamp != null) {
                version = decoder.readString();
                cid = new MavenUniqueSnapshotComponentIdentifier(componentIdentifier.getModuleIdentifier(), version, timestamp);
            }

            boolean alternativeArtifact = decoder.readBoolean();
            IvyArtifactName alternative = null;
            if (alternativeArtifact) {
                alternative = IvyArtifactNameSerializer.INSTANCE.read(decoder);
            }
            boolean optional = decoder.readBoolean();

            if (optional) {
                artifacts.add(new ModuleComponentOptionalArtifactMetadata(cid, artifactName));
            } else {
                if (alternativeArtifact) {
                    artifacts.add(new DefaultModuleComponentArtifactMetadata(cid, artifactName,
                            new DefaultModuleComponentArtifactMetadata(cid, alternative)));
                } else {
                    artifacts.add(new DefaultModuleComponentArtifactMetadata(cid, artifactName));
                }
            }
        }
        int filesCount = decoder.readSmallInt();
        for (int i = 0; i < filesCount; i++) {
            String fileName = decoder.readString();
            String uri = decoder.readString();
            artifacts.add(new UrlBackedArtifactMetadata(componentIdentifier, fileName, uri));
        }
        return artifacts.build();
    }

    protected List<ExcludeMetadata> readMavenExcludes(Decoder decoder) throws IOException {
        int excludeCount = decoder.readSmallInt();
        List<ExcludeMetadata> excludes = Lists.newArrayListWithCapacity(excludeCount);
        for (int i = 0; i < excludeCount; i++) {
            String group = decoder.readString();
            String name = decoder.readString();
            excludes.add(excludeRuleConverter.createExcludeRule(group, name));
        }
        return excludes;
    }

    protected ImmutableCapabilities readCapabilities(Decoder decoder) throws IOException {
        int capabilitiesCount = decoder.readSmallInt();
        List<Capability> rawCapabilities = Lists.newArrayListWithCapacity(capabilitiesCount);
        for (int j = 0; j < capabilitiesCount; j++) {
            String appendix = decoder.readNullableString();
            CapabilityInternal capability = new DefaultImmutableCapability(decoder.readString(), decoder.readString(), decoder.readString());
            if (appendix != null) {
                capability = new ShadowedImmutableCapability(capability, appendix);
            }
            rawCapabilities.add(capability);
        }
        return ImmutableCapabilities.of(rawCapabilities);
    }

    protected void writeFiles(Encoder encoder, ImmutableList<? extends ComponentArtifactMetadata> artifacts) throws IOException {
        int fileArtifactsCount = (int) artifacts.stream().filter(a -> a instanceof UrlBackedArtifactMetadata).count();
        int ivyArtifactsCount = artifacts.size() - fileArtifactsCount;
        encoder.writeSmallInt(ivyArtifactsCount);
        for (ComponentArtifactMetadata artifact : artifacts) {
            if (!(artifact instanceof UrlBackedArtifactMetadata)) {
                IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.getName());
                ComponentIdentifier componentId = artifact.getComponentId();
                if (componentId instanceof MavenUniqueSnapshotComponentIdentifier) {
                    MavenUniqueSnapshotComponentIdentifier uid = (MavenUniqueSnapshotComponentIdentifier) componentId;
                    encoder.writeNullableString(uid.getTimestamp());
                    encoder.writeString(uid.getVersion());
                } else {
                    encoder.writeNullableString(null);
                }
                encoder.writeBoolean(artifact.getAlternativeArtifact().isPresent());
                if (artifact.getAlternativeArtifact().isPresent()) {
                    IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.getAlternativeArtifact().get().getName());
                }
                encoder.writeBoolean(artifact.isOptionalArtifact());
            }
        }
        encoder.writeSmallInt(fileArtifactsCount);
        for (ComponentArtifactMetadata file : artifacts) {
            if (file instanceof UrlBackedArtifactMetadata) {
                encoder.writeString(((UrlBackedArtifactMetadata) file).getFileName());
                encoder.writeString(((UrlBackedArtifactMetadata) file).getRelativeUrl());
            }
        }
    }

    protected abstract void writeDependencies(Encoder encoder, ConfigurationMetadata configuration, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException;

    private void writeCapabilities(Encoder encoder, List<? extends Capability> capabilities) throws IOException {
        encoder.writeSmallInt(capabilities.size());
        for (Capability capability: capabilities) {
            boolean shadowed = capability instanceof ShadowedCapability;
            if (shadowed) {
                ShadowedCapability shadowedCapability = (ShadowedCapability) capability;
                encoder.writeNullableString(shadowedCapability.getAppendix());
                capability = shadowedCapability.getShadowedCapability();
            } else {
                encoder.writeNullableString(null);
            }
            encoder.writeString(capability.getGroup());
            encoder.writeString(capability.getName());
            encoder.writeString(capability.getVersion());
        }
    }

    protected void writeDependencyMetadata(Encoder encoder, GradleDependencyMetadata dependencyMetadata) throws IOException {
        componentSelectorSerializer.write(encoder, dependencyMetadata.getSelector());
        List<ExcludeMetadata> excludes = dependencyMetadata.getExcludes();
        writeMavenExcludeRules(encoder, excludes);
        encoder.writeBoolean(dependencyMetadata.isConstraint());
        encoder.writeBoolean(dependencyMetadata.isEndorsingStrictVersions());
        encoder.writeBoolean(dependencyMetadata.isForce());
        encoder.writeNullableString(dependencyMetadata.getReason());
        IvyArtifactNameSerializer.INSTANCE.writeNullable(encoder, dependencyMetadata.getDependencyArtifact());
    }

    protected void writeMavenExcludeRules(Encoder encoder, List<ExcludeMetadata> excludes) throws IOException {
        encoder.writeSmallInt(excludes.size());
        for (ExcludeMetadata exclude : excludes) {
            encoder.writeString(exclude.getModuleId().getGroup());
            encoder.writeString(exclude.getModuleId().getName());
        }
    }

    protected DefaultExclude readExcludeRule(Decoder decoder) throws IOException {
        String moduleOrg = decoder.readString();
        String moduleName = decoder.readString();
        IvyArtifactName artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder);
        String[] confs = readStringSet(decoder).toArray(new String[0]);
        String matcher = decoder.readNullableString();
        return new DefaultExclude(moduleIdentifierFactory.module(moduleOrg, moduleName), artifactName, confs, matcher);
    }

    protected Set<String> readStringSet(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        Set<String> set = new LinkedHashSet<>(3 * size / 2, 0.9f);
        for (int i = 0; i < size; i++) {
            set.add(decoder.readString());
        }
        return set;
    }

    protected void writeStringSet(Encoder encoder, Set<String> values) throws IOException {
        encoder.writeSmallInt(values.size());
        for (String configuration : values) {
            encoder.writeString(configuration);
        }
    }
}
