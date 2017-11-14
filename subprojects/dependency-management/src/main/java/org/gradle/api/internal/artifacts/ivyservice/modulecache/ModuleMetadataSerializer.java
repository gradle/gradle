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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.CoercingStringValueSnapshot;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.ComponentVariantResolveMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultMutableIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.DefaultMutableMavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.IvyDependencyMetadata;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.MavenDependencyMetadata;
import org.gradle.internal.component.external.model.MavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableComponentVariant;
import org.gradle.internal.component.external.model.MutableComponentVariantResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleMetadataSerializer {
    private static final byte TYPE_IVY = 1;
    private static final byte TYPE_MAVEN = 2;
    private static final byte STRING_ATTRIBUTE = 1;
    private static final byte BOOLEAN_ATTRIBUTE = 2;
    private static final ModuleComponentSelectorSerializer COMPONENT_SELECTOR_SERIALIZER = new ModuleComponentSelectorSerializer();
    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator instantiator;

    public ModuleMetadataSerializer(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
    }

    public MutableModuleComponentResolveMetadata read(Decoder decoder, ImmutableModuleIdentifierFactory moduleIdentifierFactory) throws IOException {
        return new Reader(decoder, moduleIdentifierFactory, attributesFactory, instantiator).read();
    }

    public void write(Encoder encoder, ModuleComponentResolveMetadata metadata) throws IOException {
        new Writer(encoder).write(metadata);
    }

    private static class Writer {
        private final Encoder encoder;

        private Writer(Encoder encoder) {
            this.encoder = encoder;
        }

        public void write(ModuleComponentResolveMetadata metadata) throws IOException {
            if (metadata instanceof IvyModuleResolveMetadata) {
                write((IvyModuleResolveMetadata) metadata);
            } else if (metadata instanceof MavenModuleResolveMetadata) {
                write((MavenModuleResolveMetadata) metadata);
            } else {
                throw new IllegalArgumentException("Unexpected metadata type: " + metadata.getClass());
            }
        }

        private void write(MavenModuleResolveMetadata metadata) throws IOException {
            encoder.writeByte(TYPE_MAVEN);
            writeInfoSection(metadata);
            writeDependencies(metadata.getDependencies());
            writeSharedInfo(metadata);
            writeNullableString(metadata.getSnapshotTimestamp());
            writeNullableString(metadata.getPackaging());
            writeBoolean(metadata.isRelocated());
            writeVariants(metadata);
        }

        private void writeVariants(ComponentVariantResolveMetadata metadata) throws IOException {
            encoder.writeSmallInt(metadata.getVariants().size());
            for (ComponentVariant variant : metadata.getVariants()) {
                encoder.writeString(variant.getName());
                writeAttributes(variant.getAttributes());
                writeVariantDependencies(variant.getDependencies());
                writeVariantFiles(variant.getFiles());
            }
        }

        private void writeVariantDependencies(List<? extends ComponentVariant.Dependency> dependencies) throws IOException {
            encoder.writeSmallInt(dependencies.size());
            for (ComponentVariant.Dependency dependency : dependencies) {
                COMPONENT_SELECTOR_SERIALIZER.write(encoder, dependency.getGroup(), dependency.getModule(), dependency.getVersionConstraint());
            }
        }

        private void writeAttributes(AttributeContainer attributes) throws IOException {
            encoder.writeSmallInt(attributes.keySet().size());
            for (Attribute<?> attribute : attributes.keySet()) {
                encoder.writeString(attribute.getName());
                if (attribute.getType().equals(Boolean.class)) {
                    encoder.writeByte(BOOLEAN_ATTRIBUTE);
                    encoder.writeBoolean((Boolean)attributes.getAttribute(attribute));
                } else {
                    assert attribute.getType().equals(String.class);
                    encoder.writeByte(STRING_ATTRIBUTE);
                    encoder.writeString((String) attributes.getAttribute(attribute));
                }
            }
        }

        private void writeVariantFiles(List<? extends ComponentVariant.File> files) throws IOException {
            encoder.writeSmallInt(files.size());
            for (ComponentVariant.File file : files) {
                encoder.writeString(file.getName());
                encoder.writeString(file.getUri());
            }
        }

        private void write(IvyModuleResolveMetadata metadata) throws IOException {
            encoder.writeByte(TYPE_IVY);
            writeInfoSection(metadata);
            writeExtraInfo(metadata.getExtraAttributes());
            writeConfigurations(metadata.getConfigurationDefinitions().values());
            writeDependencies(metadata.getDependencies());
            writeArtifacts(metadata.getArtifactDefinitions());
            writeExcludeRules(metadata.getExcludes());
            writeSharedInfo(metadata);
            writeNullableString(metadata.getBranch());
        }

        private void writeSharedInfo(ModuleComponentResolveMetadata metadata) throws IOException {
            encoder.writeBinary(metadata.getContentHash().asByteArray());
            encoder.writeBoolean(metadata.isMissing());
            encoder.writeString(metadata.getStatus());
        }

        private void writeId(ModuleComponentIdentifier componentIdentifier) throws IOException {
            writeString(componentIdentifier.getGroup());
            writeString(componentIdentifier.getModule());
            writeString(componentIdentifier.getVersion());
        }

        private void writeInfoSection(ModuleComponentResolveMetadata metadata) throws IOException {
            writeId(metadata.getComponentId());
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
                IvyArtifactName artifactName = artifact.getArtifactName();
                writeString(artifactName.getName());
                writeString(artifactName.getType());
                writeNullableString(artifactName.getExtension());
                writeNullableString(artifactName.getClassifier());
                writeStringSet(artifact.getConfigurations());
            }
        }

        private void writeDependencies(List<? extends ModuleDependencyMetadata> dependencies) throws IOException {
            writeCount(dependencies.size());
            for (ModuleDependencyMetadata dd : dependencies) {
                writeDependency(dd);
            }
        }

        private void writeDependency(ModuleDependencyMetadata dep) throws IOException {
            ModuleComponentSelector selector = dep.getSelector();
            COMPONENT_SELECTOR_SERIALIZER.write(encoder, selector);

            if (dep instanceof IvyDependencyMetadata) {
                IvyDependencyMetadata ivyDependency = (IvyDependencyMetadata) dep;
                encoder.writeByte(TYPE_IVY);
                writeDependencyConfigurationMapping(ivyDependency);
                writeArtifacts(ivyDependency.getDependencyArtifacts());
                writeExcludeRules(ivyDependency.getExcludes());
                writeString(ivyDependency.getDynamicConstraintVersion());
                writeBoolean(ivyDependency.isForce());
                writeBoolean(ivyDependency.isChanging());
                writeBoolean(ivyDependency.isTransitive());
                writeBoolean(ivyDependency.isOptional());
            } else if (dep instanceof MavenDependencyMetadata) {
                MavenDependencyMetadata mavenDependency = (MavenDependencyMetadata) dep;
                encoder.writeByte(TYPE_MAVEN);
                writeArtifacts(mavenDependency.getDependencyArtifacts());
                writeExcludeRules(mavenDependency.getExcludes());
                encoder.writeSmallInt(mavenDependency.getScope().ordinal());
                encoder.writeBoolean(mavenDependency.isOptional());
            } else {
                throw new IllegalStateException("Unexpected dependency type");
            }
        }

        private void writeDependencyConfigurationMapping(IvyDependencyMetadata dep) throws IOException {
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
                IvyArtifactName artifact = exclude.getArtifact();
                writeString(exclude.getModuleId().getGroup());
                writeString(exclude.getModuleId().getName());
                writeString(artifact.getName());
                writeString(artifact.getType());
                writeString(artifact.getExtension());
                writeStringArray(exclude.getConfigurations().toArray(new String[0]));
                writeString(exclude.getMatcher());
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
        private final ImmutableAttributesFactory attributesFactory;
        private final NamedObjectInstantiator instantiator;
        private ModuleComponentIdentifier id;
        private ModuleVersionIdentifier mvi;

        private Reader(Decoder decoder, ImmutableModuleIdentifierFactory moduleIdentifierFactory, ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator) {
            this.decoder = decoder;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.attributesFactory = attributesFactory;
            this.instantiator = instantiator;
        }

        public MutableModuleComponentResolveMetadata read() throws IOException {
            byte type = decoder.readByte();
            switch (type) {
                case TYPE_IVY:
                    return readIvy();
                case TYPE_MAVEN:
                    return readMaven();
                default:
                    throw new IllegalArgumentException("Unexpected metadata type found.");
            }
        }

        private void readSharedInfo(MutableModuleComponentResolveMetadata metadata) throws IOException {
            metadata.setContentHash(new HashValue(decoder.readBinary()));
            metadata.setMissing(decoder.readBoolean());
            metadata.setStatus(decoder.readString());
        }

        private MutableModuleComponentResolveMetadata readMaven() throws IOException {
            readInfoSection();
            List<ModuleDependencyMetadata> dependencies = readDependencies();
            DefaultMutableMavenModuleResolveMetadata metadata = new DefaultMutableMavenModuleResolveMetadata(mvi, id, dependencies);
            readSharedInfo(metadata);
            metadata.setSnapshotTimestamp(readNullableString());
            metadata.setPackaging(readNullableString());
            metadata.setRelocated(readBoolean());
            readVariants(metadata);
            return metadata;
        }

        private void readVariants(MutableComponentVariantResolveMetadata metadata) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                String name = decoder.readString();
                ImmutableAttributes attributes = readAttributes();
                MutableComponentVariant variant = metadata.addVariant(name, attributes);
                readVariantDependencies(variant);
                readVariantFiles(variant);
            }
        }

        private ImmutableAttributes readAttributes() throws IOException {
            ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                String name = decoder.readString();
                byte type = decoder.readByte();
                if (type == BOOLEAN_ATTRIBUTE) {
                    attributes = attributesFactory.concat(attributes, Attribute.of(name, Boolean.class), decoder.readBoolean());
                } else {
                    String value = decoder.readString();
                    attributes = attributesFactory.concat(attributes, Attribute.of(name, String.class), new CoercingStringValueSnapshot(value, instantiator));
                }
            }
            return attributes;
        }

        private void readVariantDependencies(MutableComponentVariant variant) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                ModuleComponentSelector selector = COMPONENT_SELECTOR_SERIALIZER.read(decoder);
                variant.addDependency(selector.getGroup(), selector.getModule(), selector.getVersionConstraint());
            }
        }

        private void readVariantFiles(MutableComponentVariant variant) throws IOException {
            int count = decoder.readSmallInt();
            for (int i = 0; i < count; i++) {
                variant.addFile(decoder.readString(), decoder.readString());
            }
        }

        private MutableModuleComponentResolveMetadata readIvy() throws IOException {
            readInfoSection();
            Map<NamespaceId, String> extraAttributes = readExtraInfo();
            List<Configuration> configurations = readConfigurations();
            List<ModuleDependencyMetadata> dependencies = readDependencies();
            List<Artifact> artifacts = readArtifacts();
            List<Exclude> excludes = readAllExcludes();
            DefaultMutableIvyModuleResolveMetadata metadata = new DefaultMutableIvyModuleResolveMetadata(mvi, id, configurations, dependencies, artifacts);
            readSharedInfo(metadata);
            String branch = readNullableString();
            metadata.setBranch(branch);
            metadata.setExtraAttributes(extraAttributes);
            metadata.setExcludes(excludes);
            return metadata;
        }

        private void readInfoSection() throws IOException {
            id = readId();
            mvi = moduleIdentifierFactory.moduleWithVersion(id.getGroup(), id.getModule(), id.getVersion());
        }

        private ModuleComponentIdentifier readId() throws IOException {
            return DefaultModuleComponentIdentifier.newId(readString(), readString(), readString());
        }

        private Map<NamespaceId, String> readExtraInfo() throws IOException {
            int len = readCount();
            Map<NamespaceId, String> result = new LinkedHashMap<NamespaceId, String>(len);
            for (int i = 0; i < len; i++) {
                NamespaceId namespaceId = new NamespaceId(readString(), readString());
                String value = readString();
                result.put(namespaceId, value);
            }
            return result;
        }

        private List<Configuration> readConfigurations() throws IOException {
            int len = readCount();
            List<Configuration> configurations = new ArrayList<Configuration>(len);
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
                IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(readString(), readString(), readNullableString(), readNullableString());
                result.add(new Artifact(ivyArtifactName, readStringSet()));
            }
            return result;
        }

        private List<ModuleDependencyMetadata> readDependencies() throws IOException {
            int len = readCount();
            List<ModuleDependencyMetadata> result = Lists.newArrayListWithCapacity(len);
            for (int i = 0; i < len; i++) {
                result.add(readDependency());
            }
            return result;
        }

        private ModuleDependencyMetadata readDependency() throws IOException {
            ModuleComponentSelector requested = COMPONENT_SELECTOR_SERIALIZER.read(decoder);

            byte type = decoder.readByte();
            switch (type) {
                case TYPE_IVY:
                    SetMultimap<String, String> configMappings = readDependencyConfigurationMapping();
                    List<Artifact> artifacts = readDependencyArtifactDescriptors();
                    List<Exclude> excludes = readExcludeRules();
                    String dynamicConstraintVersion = readString();
                    boolean force = readBoolean();
                    boolean changing = readBoolean();
                    boolean transitive = readBoolean();
                    boolean optional = readBoolean();
                    return new IvyDependencyMetadata(requested, dynamicConstraintVersion, force, changing, transitive,  optional, configMappings, artifacts, excludes);
                case TYPE_MAVEN:
                    artifacts = readDependencyArtifactDescriptors();
                    excludes = readExcludeRules();
                    MavenScope scope = MavenScope.values()[decoder.readSmallInt()];
                    optional = decoder.readBoolean();
                    return new MavenDependencyMetadata(scope, optional, requested, artifacts, excludes);
                default:
                    throw new IllegalArgumentException("Unexpected dependency type found.");
            }
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
                IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(readString(), readString(), readNullableString(), readNullableString());
                result.add(new Artifact(ivyArtifactName, readStringSet()));
            }
            return result;
        }

        private List<Exclude> readExcludeRules() throws IOException {
            int len = readCount();
            List<Exclude> result = Lists.newArrayListWithCapacity(len);
            for (int i = 0; i < len; i++) {
                DefaultExclude rule = readExcludeRule();
                result.add(rule);
            }
            return result;
        }

        private DefaultExclude readExcludeRule() throws IOException {
            String moduleOrg = readString();
            String moduleName = readString();
            String artifact = readString();
            String type = readString();
            String ext = readString();
            String[] confs = readStringArray();
            String matcher = readString();
            return new DefaultExclude(moduleIdentifierFactory.module(moduleOrg, moduleName), artifact, type, ext, confs, matcher);
        }

        private List<Exclude> readAllExcludes() throws IOException {
            int len = readCount();
            List<Exclude> result = new ArrayList<Exclude>(len);
            for (int i = 0; i < len; i++) {
                result.add(readExcludeRule());
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
            List<String> list = new ArrayList<String>(size);
            for (int i = 0; i < size; i++) {
                list.add(readString());
            }
            return list;
        }

        private Set<String> readStringSet() throws IOException {
            int size = readCount();
            Set<String> set = new LinkedHashSet<String>(size);
            for (int i = 0; i < size; i++) {
                set.add(readString());
            }
            return set;
        }
    }

}
