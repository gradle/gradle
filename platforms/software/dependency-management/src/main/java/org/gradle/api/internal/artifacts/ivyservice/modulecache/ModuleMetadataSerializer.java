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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableComponentVariant;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ShadowedImmutableCapability;
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;
import org.gradle.internal.component.external.model.maven.MavenDependencyType;
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleMetadataSerializer {
    private static final byte TYPE_IVY = 1;
    private static final byte TYPE_MAVEN = 2;

    private final ModuleComponentSelectorSerializer componentSelectorSerializer;
    private final MavenMutableModuleMetadataFactory mavenMetadataFactory;
    private final IvyMutableModuleMetadataFactory ivyMetadataFactory;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final ModuleSourcesSerializer moduleSourcesSerializer;

    public ModuleMetadataSerializer(AttributeContainerSerializer attributeContainerSerializer, MavenMutableModuleMetadataFactory mavenMetadataFactory, IvyMutableModuleMetadataFactory ivyMetadataFactory, ModuleSourcesSerializer moduleSourcesSerializer) {
        this.mavenMetadataFactory = mavenMetadataFactory;
        this.ivyMetadataFactory = ivyMetadataFactory;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.componentSelectorSerializer = new ModuleComponentSelectorSerializer(attributeContainerSerializer);
        this.moduleSourcesSerializer = moduleSourcesSerializer;
    }

    public MutableModuleComponentResolveMetadata read(Decoder decoder, ImmutableModuleIdentifierFactory moduleIdentifierFactory, Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
        return new Reader(decoder, moduleIdentifierFactory, attributeContainerSerializer, componentSelectorSerializer, mavenMetadataFactory, ivyMetadataFactory, moduleSourcesSerializer).read(deduplicationDependencyCache);
    }

    public void write(Encoder encoder, ModuleComponentResolveMetadata metadata, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        new Writer(encoder, attributeContainerSerializer, componentSelectorSerializer, moduleSourcesSerializer).write(metadata, deduplicationDependencyCache);
    }

    private static class Writer {
        private final Encoder encoder;
        private final AttributeContainerSerializer attributeContainerSerializer;
        private final ModuleComponentSelectorSerializer componentSelectorSerializer;
        private final ModuleSourcesSerializer moduleSourcesSerializer;

        private Writer(Encoder encoder, AttributeContainerSerializer attributeContainerSerializer, ModuleComponentSelectorSerializer componentSelectorSerializer, ModuleSourcesSerializer moduleSourcesSerializer) {
            this.encoder = encoder;
            this.attributeContainerSerializer = attributeContainerSerializer;
            this.componentSelectorSerializer = componentSelectorSerializer;
            this.moduleSourcesSerializer = moduleSourcesSerializer;
        }

        public void write(ModuleComponentResolveMetadata metadata, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
            if (metadata instanceof IvyModuleResolveMetadata) {
                write((IvyModuleResolveMetadata) metadata);
            } else if (metadata instanceof MavenModuleResolveMetadata) {
                write((MavenModuleResolveMetadata) metadata, deduplicationDependencyCache);
            } else {
                throw new IllegalArgumentException("Unexpected metadata type: " + metadata.getClass());
            }
        }

        private void write(MavenModuleResolveMetadata metadata, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
            encoder.writeByte(TYPE_MAVEN);
            writeInfoSection(metadata);
            writeNullableString(metadata.getSnapshotTimestamp());
            writeMavenDependencies(metadata.getDependencies(), deduplicationDependencyCache);
            writeSharedInfo(metadata);
            // NOTE: This looks nullable, but only non-null Strings are provided. Changing this to write a non-null string would not be backwards compatible.
            writeNullableString(metadata.getPackaging());
            writeBoolean(metadata.isRelocated());
            writeVariants(metadata);
        }

        private void writeVariants(ModuleComponentResolveMetadata metadata) throws IOException {
            encoder.writeSmallInt(metadata.getVariants().size());
            for (ComponentVariant variant : metadata.getVariants()) {
                encoder.writeString(variant.getName());
                writeAttributes(variant.getAttributes());
                writeVariantDependencies(variant.getDependencies());
                writeVariantConstraints(variant.getDependencyConstraints());
                writeVariantFiles(variant.getFiles());
                writeVariantCapabilities(variant.getCapabilities());
                encoder.writeBoolean(variant.isExternalVariant());
            }
        }

        private void writeVariantConstraints(ImmutableList<? extends ComponentVariant.DependencyConstraint> constraints) throws IOException {
            encoder.writeSmallInt(constraints.size());
            for (ComponentVariant.DependencyConstraint constraint : constraints) {
                componentSelectorSerializer.write(encoder, constraint.getGroup(), constraint.getModule(), constraint.getVersionConstraint(), constraint.getAttributes(), Collections.emptyList());
                encoder.writeNullableString(constraint.getReason());
            }
        }

        private void writeVariantDependencies(List<? extends ComponentVariant.Dependency> dependencies) throws IOException {
            encoder.writeSmallInt(dependencies.size());
            for (ComponentVariant.Dependency dependency : dependencies) {
                componentSelectorSerializer.write(encoder, dependency.getGroup(), dependency.getModule(), dependency.getVersionConstraint(), dependency.getAttributes(), dependency.getRequestedCapabilities());
                encoder.writeNullableString(dependency.getReason());
                writeVariantDependencyExcludes(dependency.getExcludes());
                encoder.writeBoolean(dependency.isEndorsingStrictVersions());
                writeNullableArtifact(dependency.getDependencyArtifact());
            }
        }

        private void writeVariantDependencyExcludes(List<ExcludeMetadata> excludes) throws IOException {
            writeCount(excludes.size());
            for (ExcludeMetadata exclude : excludes) {
                writeString(exclude.getModuleId().getGroup());
                writeString(exclude.getModuleId().getName());
            }
        }

        private void writeAttributes(AttributeContainer attributes) throws IOException {
            attributeContainerSerializer.write(encoder, attributes);
        }

        private void writeVariantFiles(List<? extends ComponentVariant.File> files) throws IOException {
            encoder.writeSmallInt(files.size());
            for (ComponentVariant.File file : files) {
                encoder.writeString(file.getName());
                encoder.writeString(file.getUri());
            }
        }

        private void writeVariantCapabilities(ImmutableCapabilities capabilities) throws IOException {
            ImmutableSet<ImmutableCapability> capabilitySet = capabilities.asSet();
            encoder.writeSmallInt(capabilitySet.size());
            for (Capability capability : capabilitySet) {
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


        private void write(IvyModuleResolveMetadata metadata) throws IOException {
            encoder.writeByte(TYPE_IVY);
            writeInfoSection(metadata);
            writeExtraInfo(metadata.getExtraAttributes());
            writeConfigurations(metadata.getConfigurationDefinitions().values());
            writeIvyDependencies(metadata.getDependencies());
            writeArtifacts(metadata.getArtifactDefinitions());
            writeExcludeRules(metadata.getExcludes());
            writeSharedInfo(metadata);
            writeNullableString(metadata.getBranch());
            writeVariants(metadata);
        }

        private void writeSharedInfo(ModuleComponentResolveMetadata metadata) throws IOException {
            encoder.writeBoolean(metadata.isMissing());
            encoder.writeBoolean(metadata.isChanging());
            encoder.writeBoolean(metadata.isExternalVariant());
            encoder.writeString(metadata.getStatus());
            writeStringList(metadata.getStatusScheme());
            moduleSourcesSerializer.write(encoder, metadata.getSources());
        }

        private void writeId(ModuleComponentIdentifier componentIdentifier) throws IOException {
            writeString(componentIdentifier.getGroup());
            writeString(componentIdentifier.getModule());
            writeString(componentIdentifier.getVersion());
        }

        private void writeInfoSection(ModuleComponentResolveMetadata metadata) throws IOException {
            writeId(metadata.getId());
            writeAttributes(metadata.getAttributes());
        }

        private void writeExtraInfo(Map<NamespaceId, String> extraInfo) throws IOException {
            writeCount(extraInfo.size());
            for (Map.Entry<NamespaceId, String> entry : extraInfo.entrySet()) {
                NamespaceId namespaceId = entry.getKey();
                writeString(namespaceId.getNamespace());
                writeString(namespaceId.getName());
                writeString(entry.getValue());
            }
        }

        private void writeConfigurations(Collection<Configuration> configurations) throws IOException {
            writeCount(configurations.size());
            for (Configuration conf : configurations) {
                writeConfiguration(conf);
            }
        }

        private void writeConfiguration(Configuration conf) throws IOException {
            writeString(conf.getName());
            writeBoolean(conf.isTransitive());
            writeBoolean(conf.isVisible());
            writeStringList(conf.getExtendsFrom());
        }

        private void writeArtifacts(List<Artifact> artifacts) throws IOException {
            writeCount(artifacts.size());
            for (Artifact artifact : artifacts) {
                IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact.getArtifactName());
                writeStringSet(artifact.getConfigurations());
            }
        }

        private void writeIvyDependencies(List<IvyDependencyDescriptor> dependencies) throws IOException {
            writeCount(dependencies.size());
            for (IvyDependencyDescriptor dd : dependencies) {
                writeIvyDependency(dd);
            }
        }

        private void writeMavenDependencies(List<MavenDependencyDescriptor> dependencies, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
            writeCount(dependencies.size());
            for (MavenDependencyDescriptor dd : dependencies) {
                writeMavenDependency(dd, deduplicationDependencyCache);
            }
        }

        private void writeIvyDependency(IvyDependencyDescriptor ivyDependency) throws IOException {
            componentSelectorSerializer.write(encoder, ivyDependency.getSelector());
            writeDependencyConfigurationMapping(ivyDependency);
            writeArtifacts(ivyDependency.getDependencyArtifacts());
            writeExcludeRules(ivyDependency.getAllExcludes());
            writeString(ivyDependency.getDynamicConstraintVersion());
            writeBoolean(ivyDependency.isChanging());
            writeBoolean(ivyDependency.isTransitive());
            writeBoolean(ivyDependency.isOptional());
        }

        private void writeDependencyConfigurationMapping(IvyDependencyDescriptor dep) throws IOException {
            SetMultimap<String, String> confMappings = dep.getConfMappings();
            writeCount(confMappings.keySet().size());
            for (String conf : confMappings.keySet()) {
                writeString(conf);
                writeStringSet(confMappings.get(conf));
            }
        }

        private void writeExcludeRules(List<Exclude> excludes) throws IOException {
            writeCount(excludes.size());
            for (Exclude exclude : excludes) {
                writeString(exclude.getModuleId().getGroup());
                writeString(exclude.getModuleId().getName());
                IvyArtifactName artifact = exclude.getArtifact();
                writeNullableArtifact(artifact);
                writeStringArray(exclude.getConfigurations().toArray(new String[0]));
                writeNullableString(exclude.getMatcher());
            }
        }

        private void writeMavenDependency(MavenDependencyDescriptor mavenDependency, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
            int nextMapping = deduplicationDependencyCache.size();
            Integer mapping = deduplicationDependencyCache.putIfAbsent(mavenDependency, nextMapping);
            if (mapping != null) {
                // Save a reference to the dependency that was written before
                encoder.writeSmallInt(mapping);
            } else {
                encoder.writeSmallInt(nextMapping);
                componentSelectorSerializer.write(encoder, mavenDependency.getSelector());
                writeNullableArtifact(mavenDependency.getDependencyArtifact());
                writeMavenExcludeRules(mavenDependency.getAllExcludes());
                encoder.writeSmallInt(mavenDependency.getScope().ordinal());
                encoder.writeSmallInt(mavenDependency.getType().ordinal());
            }
        }

        private void writeNullableArtifact(IvyArtifactName artifact) throws IOException {
            if (artifact == null) {
                writeBoolean(false);
            } else {
                writeBoolean(true);
                IvyArtifactNameSerializer.INSTANCE.write(encoder, artifact);
            }
        }

        private void writeMavenExcludeRules(List<ExcludeMetadata> excludes) throws IOException {
            writeCount(excludes.size());
            for (ExcludeMetadata exclude : excludes) {
                writeString(exclude.getModuleId().getGroup());
                writeString(exclude.getModuleId().getName());
            }
        }

        private void writeCount(int i) throws IOException {
            encoder.writeSmallInt(i);
        }

        private void writeString(String str) throws IOException {
            encoder.writeString(str);
        }

        private void writeNullableString(String str) throws IOException {
            encoder.writeNullableString(str);
        }

        private void writeBoolean(boolean b) throws IOException {
            encoder.writeBoolean(b);
        }

        private void writeStringArray(String[] values) throws IOException {
            writeCount(values.length);
            for (String configuration : values) {
                writeNullableString(configuration);
            }
        }

        private void writeStringList(List<String> values) throws IOException {
            writeCount(values.size());
            for (String configuration : values) {
                writeString(configuration);
            }
        }

        private void writeStringSet(Set<String> values) throws IOException {
            writeCount(values.size());
            for (String configuration : values) {
                writeString(configuration);
            }
        }
    }

    private static class Reader {
        private final Decoder decoder;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final ExcludeRuleConverter excludeRuleConverter;
        private final AttributeContainerSerializer attributeContainerSerializer;
        private final ModuleComponentSelectorSerializer componentSelectorSerializer;
        private final MavenMutableModuleMetadataFactory mavenMetadataFactory;
        private final IvyMutableModuleMetadataFactory ivyMetadataFactory;
        private final ModuleSourcesSerializer moduleSourcesSerializer;
        private ModuleComponentIdentifier id;
        private ImmutableAttributes attributes;

        private Reader(Decoder decoder,
                       ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                       AttributeContainerSerializer attributeContainerSerializer,
                       ModuleComponentSelectorSerializer componentSelectorSerializer, MavenMutableModuleMetadataFactory mavenMutableModuleMetadataFactory,
                       IvyMutableModuleMetadataFactory ivyMetadataFactory,
                       ModuleSourcesSerializer moduleSourcesSerializer) {
            this.decoder = decoder;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.excludeRuleConverter = new DefaultExcludeRuleConverter(moduleIdentifierFactory);
            this.attributeContainerSerializer = attributeContainerSerializer;
            this.componentSelectorSerializer = componentSelectorSerializer;
            this.mavenMetadataFactory = mavenMutableModuleMetadataFactory;
            this.ivyMetadataFactory = ivyMetadataFactory;
            this.moduleSourcesSerializer = moduleSourcesSerializer;
        }

        public MutableModuleComponentResolveMetadata read(Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
            byte type = decoder.readByte();
            switch (type) {
                case TYPE_IVY:
                    return readIvy();
                case TYPE_MAVEN:
                    return readMaven(deduplicationDependencyCache);
                default:
                    throw new IllegalArgumentException("Unexpected metadata type found.");
            }
        }

        private void readSharedInfo(MutableModuleComponentResolveMetadata metadata) throws IOException {
            metadata.setMissing(decoder.readBoolean());
            metadata.setChanging(decoder.readBoolean());
            metadata.setExternalVariant(decoder.readBoolean());
            metadata.setStatus(decoder.readString());
            metadata.setStatusScheme(readStringList());
            metadata.setSources(moduleSourcesSerializer.read(decoder));
        }

        private MutableModuleComponentResolveMetadata readMaven(Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
            readInfoSection();
            String snapshotTimestamp = readNullableString();
            if (snapshotTimestamp != null) {
                id = new MavenUniqueSnapshotComponentIdentifier(id, snapshotTimestamp);
            }

            List<MavenDependencyDescriptor> dependencies = readMavenDependencies(deduplicationDependencyCache);
            MutableMavenModuleResolveMetadata metadata = mavenMetadataFactory.create(id, dependencies);
            readSharedInfo(metadata);
            metadata.setSnapshotTimestamp(snapshotTimestamp);
            // NOTE: this looks nullable, but only non-null Strings are written
            metadata.setPackaging(readNullableString());
            metadata.setRelocated(readBoolean());
            metadata.setAttributes(attributes);
            readVariants(metadata);
            return metadata;
        }

        private void readVariants(MutableModuleComponentResolveMetadata metadata) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                String name = decoder.readString();
                ImmutableAttributes attributes = readAttributes();
                MutableComponentVariant variant = metadata.addVariant(name, attributes);
                readVariantDependencies(variant);
                readVariantConstraints(variant);
                readVariantFiles(variant);
                readVariantCapabilities(variant);
                boolean externalVariant = decoder.readBoolean();
                variant.setAvailableExternally(externalVariant);
            }
        }

        private ImmutableAttributes readAttributes() throws IOException {
            return attributeContainerSerializer.read(decoder);
        }

        private void readVariantDependencies(MutableComponentVariant variant) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                ModuleComponentSelector selector = componentSelectorSerializer.read(decoder);
                String reason = decoder.readNullableString();
                ImmutableList<ExcludeMetadata> excludes = readVariantDependencyExcludes();
                boolean endorsing = decoder.readBoolean();
                IvyArtifactName dependencyArtifact = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder);
                variant.addDependency(selector.getGroup(), selector.getModule(), selector.getVersionConstraint(), excludes, reason, (ImmutableAttributes) selector.getAttributes(), selector.getRequestedCapabilities(), endorsing, dependencyArtifact);
            }
        }

        private void readVariantConstraints(MutableComponentVariant variant) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                ModuleComponentSelector selector = componentSelectorSerializer.read(decoder);
                String reason = decoder.readNullableString();
                variant.addDependencyConstraint(selector.getGroup(), selector.getModule(), selector.getVersionConstraint(), reason, (ImmutableAttributes) selector.getAttributes());
            }
        }

        private ImmutableList<ExcludeMetadata> readVariantDependencyExcludes() throws IOException {
            ImmutableList.Builder<ExcludeMetadata> builder = new ImmutableList.Builder<>();
            int len = readCount();
            for (int i = 0; i < len; i++) {
                String group = readString();
                String module = readString();
                builder.add(excludeRuleConverter.createExcludeRule(group, module));
            }
            return builder.build();
        }

        private void readVariantFiles(MutableComponentVariant variant) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                variant.addFile(decoder.readString(), decoder.readString());
            }
        }

        private void readVariantCapabilities(MutableComponentVariant variant) throws IOException {
            int capabilitiesCount = decoder.readSmallInt();
            for (int j = 0; j < capabilitiesCount; j++) {
                String appendix = decoder.readNullableString();
                CapabilityInternal capability = new DefaultImmutableCapability(decoder.readString(), decoder.readString(), decoder.readString());
                if (appendix != null) {
                    capability = new ShadowedImmutableCapability(capability, appendix);
                }
                variant.addCapability(capability);
            }
        }

        private MutableModuleComponentResolveMetadata readIvy() throws IOException {
            readInfoSection();
            Map<NamespaceId, String> extraAttributes = readExtraInfo();
            List<Configuration> configurations = readConfigurations();
            List<IvyDependencyDescriptor> dependencies = readIvyDependencies();
            List<Artifact> artifacts = readArtifacts();
            List<Exclude> excludes = readModuleExcludes();
            MutableIvyModuleResolveMetadata metadata = ivyMetadataFactory.create(id, dependencies, configurations, artifacts, excludes);
            readSharedInfo(metadata);
            String branch = readNullableString();
            metadata.setBranch(branch);
            metadata.setExtraAttributes(extraAttributes);
            metadata.setAttributes(attributes);
            readVariants(metadata);
            return metadata;
        }

        private void readInfoSection() throws IOException {
            id = readId();
            attributes = readAttributes();
        }

        private ModuleComponentIdentifier readId() throws IOException {
            return DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(readString(), readString()), readString());
        }

        private Map<NamespaceId, String> readExtraInfo() throws IOException {
            int len = readCount();
            Map<NamespaceId, String> result = new LinkedHashMap<>(len);
            for (int i = 0; i < len; i++) {
                NamespaceId namespaceId = new NamespaceId(readString(), readString());
                String value = readString();
                result.put(namespaceId, value);
            }
            return result;
        }

        private List<Configuration> readConfigurations() throws IOException {
            int len = readCount();
            List<Configuration> configurations = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                Configuration configuration = readConfiguration();
                configurations.add(configuration);
            }
            return configurations;
        }

        private Configuration readConfiguration() throws IOException {
            String name = readString();
            boolean transitive = readBoolean();
            boolean visible = readBoolean();
            List<String> extendsFrom = readStringList();
            return new Configuration(name, transitive, visible, extendsFrom);
        }

        private List<Artifact> readArtifacts() throws IOException {
            int size = readCount();
            List<Artifact> result = Lists.newArrayListWithCapacity(size);
            for (int i = 0; i < size; i++) {
                IvyArtifactName ivyArtifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder);
                result.add(new Artifact(ivyArtifactName, readStringSet()));
            }
            return result;
        }

        private List<IvyDependencyDescriptor> readIvyDependencies() throws IOException {
            int len = readCount();
            List<IvyDependencyDescriptor> result = Lists.newArrayListWithCapacity(len);
            for (int i = 0; i < len; i++) {
                result.add(readIvyDependency());
            }
            return result;
        }

        private IvyDependencyDescriptor readIvyDependency() throws IOException {
            ModuleComponentSelector requested = componentSelectorSerializer.read(decoder);
            SetMultimap<String, String> configMappings = readDependencyConfigurationMapping();
            List<Artifact> artifacts = readDependencyArtifactDescriptors();
            List<Exclude> excludes = readDependencyExcludes();
            String dynamicConstraintVersion = readString();
            boolean changing = readBoolean();
            boolean transitive = readBoolean();
            boolean optional = readBoolean();
            return new IvyDependencyDescriptor(requested, dynamicConstraintVersion, changing, transitive,  optional, configMappings, artifacts, excludes);
        }

        private SetMultimap<String, String> readDependencyConfigurationMapping() throws IOException {
            int size = readCount();
            SetMultimap<String, String> result = LinkedHashMultimap.create();
            for (int i = 0; i < size; i++) {
                String from = readString();
                Set<String> to = readStringSet();
                result.putAll(from, to);
            }
            return result;
        }

        private List<Artifact> readDependencyArtifactDescriptors() throws IOException {
            int size = readCount();
            List<Artifact> result = Lists.newArrayListWithCapacity(size);
            for (int i = 0; i < size; i++) {
                IvyArtifactName ivyArtifactName = IvyArtifactNameSerializer.INSTANCE.read(decoder);
                result.add(new Artifact(ivyArtifactName, readStringSet()));
            }
            return result;
        }

        private List<Exclude> readDependencyExcludes() throws IOException {
            int len = readCount();
            List<Exclude> result = Lists.newArrayListWithCapacity(len);
            for (int i = 0; i < len; i++) {
                DefaultExclude rule = readExcludeRule();
                result.add(rule);
            }
            return result;
        }

        private List<Exclude> readModuleExcludes() throws IOException {
            int len = readCount();
            List<Exclude> result = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                result.add(readExcludeRule());
            }
            return result;
        }

        private DefaultExclude readExcludeRule() throws IOException {
            String moduleOrg = readString();
            String moduleName = readString();
            IvyArtifactName artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder);
            String[] confs = readStringArray();
            String matcher = readNullableString();
            return new DefaultExclude(moduleIdentifierFactory.module(moduleOrg, moduleName), artifactName, confs, matcher);
        }

        private List<MavenDependencyDescriptor> readMavenDependencies(Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
            int len = readCount();
            List<MavenDependencyDescriptor> result = Lists.newArrayListWithCapacity(len);
            for (int i = 0; i < len; i++) {
                result.add(readMavenDependency(deduplicationDependencyCache));
            }
            return result;
        }

        private MavenDependencyDescriptor readMavenDependency(Map<Integer, MavenDependencyDescriptor> deduplicationDependencyCache) throws IOException {
            int mapping = decoder.readSmallInt();
            if (mapping == deduplicationDependencyCache.size()) {
                ModuleComponentSelector requested = componentSelectorSerializer.read(decoder);
                IvyArtifactName artifactName = IvyArtifactNameSerializer.INSTANCE.readNullable(decoder);
                List<ExcludeMetadata> mavenExcludes = readMavenDependencyExcludes();
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

        private List<ExcludeMetadata> readMavenDependencyExcludes() throws IOException {
            int len = readCount();
            List<ExcludeMetadata> result = Lists.newArrayListWithCapacity(len);
            for (int i = 0; i < len; i++) {
                String moduleOrg = readString();
                String moduleName = readString();
                DefaultExclude rule = new DefaultExclude(moduleIdentifierFactory.module(moduleOrg, moduleName));
                result.add(rule);
            }
            return result;
        }

        private int readCount() throws IOException {
            return decoder.readSmallInt();
        }

        private String readString() throws IOException {
            return decoder.readString();
        }

        private String readNullableString() throws IOException {
            return decoder.readNullableString();
        }

        private boolean readBoolean() throws IOException {
            return decoder.readBoolean();
        }

        private String[] readStringArray() throws IOException {
            int size = readCount();
            String[] array = new String[size];
            for (int i = 0; i < size; i++) {
                array[i] = readNullableString();
            }
            return array;
        }

        private List<String> readStringList() throws IOException {
            int size = readCount();
            ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(size);
            for (int i = 0; i < size; i++) {
                builder.add(readString());
            }
            return builder.build();
        }

        private Set<String> readStringSet() throws IOException {
            int size = readCount();
            ImmutableSet.Builder<String> builder = ImmutableSet.builderWithExpectedSize(size);
            for (int i = 0; i < size; i++) {
                builder.add(readString());
            }
            return builder.build();
        }
    }

}
