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

package org.gradle.internal.component.external.model.ivy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AbstractRealisedModuleResolveMetadataSerializationHelper;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.LazyToRealisedModuleComponentResolveMetadataHelper;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RealisedIvyModuleResolveMetadataSerializationHelper extends AbstractRealisedModuleResolveMetadataSerializationHelper {

    public RealisedIvyModuleResolveMetadataSerializationHelper(AttributeContainerSerializer attributeContainerSerializer, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        super(attributeContainerSerializer, moduleIdentifierFactory);
    }

    public ModuleComponentResolveMetadata readMetadata(Decoder decoder, DefaultIvyModuleResolveMetadata resolveMetadata) throws IOException {
        Map<String, List<GradleDependencyMetadata>> variantToDependencies = readVariantDependencies(decoder);
        ImmutableList<? extends ComponentVariant> variants = resolveMetadata.getVariants();
        ImmutableList.Builder<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> builder = ImmutableList.builder();
        for (ComponentVariant variant: variants) {
            builder.add(new AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl(resolveMetadata.getId(), variant.getName(), variant.getAttributes().asImmutable(), variant.getDependencies(), variant.getDependencyConstraints(),
                variant.getFiles(), ImmutableCapabilities.of(variant.getCapabilities().getCapabilities()), variantToDependencies.get(variant.getName())));
        }
        ImmutableList<AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl> realisedVariants = builder.build();
        return new RealisedIvyModuleResolveMetadata(resolveMetadata, realisedVariants, readIvyConfigurations(decoder, resolveMetadata));
    }

    @Override
    protected void writeDependencies(Encoder encoder, ConfigurationMetadata configuration, Map<ExternalDependencyDescriptor, Integer> deduplicationDependencyCache) throws IOException {
        List<? extends DependencyMetadata> dependencies = configuration.getDependencies();
        encoder.writeSmallInt(dependencies.size());
        for (DependencyMetadata dependency: dependencies) {
            if (dependency instanceof GradleDependencyMetadata) {
                encoder.writeByte(GRADLE_DEPENDENCY_METADATA);
                writeDependencyMetadata(encoder, (GradleDependencyMetadata) dependency);
            } else if (dependency instanceof ConfigurationBoundExternalDependencyMetadata) {
                ConfigurationBoundExternalDependencyMetadata dependencyMetadata = (ConfigurationBoundExternalDependencyMetadata) dependency;
                ExternalDependencyDescriptor dependencyDescriptor = dependencyMetadata.getDependencyDescriptor();
                if (dependencyDescriptor instanceof IvyDependencyDescriptor) {
                    encoder.writeByte(IVY_DEPENDENCY_METADATA);
                    boolean addedByRule = configuration instanceof RealisedConfigurationMetadata && ((RealisedConfigurationMetadata) configuration).isAddedByRule();
                    writeIvyDependency(encoder, (IvyDependencyDescriptor) dependencyDescriptor, configuration.getName(), addedByRule);
                } else {
                    throw new IllegalStateException("Unknown type of dependency descriptor: " + dependencyDescriptor.getClass());
                }
                encoder.writeNullableString(dependency.getReason());
            }
        }
    }

    @Override
    protected void writeConfiguration(Encoder encoder, ConfigurationMetadata configuration) throws IOException {
        super.writeConfiguration(encoder, configuration);
        if (configuration instanceof RealisedConfigurationMetadata) {
            RealisedConfigurationMetadata realisedMetadata = (RealisedConfigurationMetadata) configuration;
            if (realisedMetadata.isAddedByRule()) {
                encoder.writeBoolean(true);
                writeMavenExcludeRules(encoder, realisedMetadata.getExcludes());
            } else {
                encoder.writeBoolean(false);
            }
        } else {
            encoder.writeBoolean(false);
        }
    }

    private Map<String, ConfigurationMetadata> readIvyConfigurations(Decoder decoder, DefaultIvyModuleResolveMetadata metadata) throws IOException {
        IvyConfigurationHelper configurationHelper = new IvyConfigurationHelper(metadata.getArtifactDefinitions(), new IdentityHashMap<>(), metadata.getExcludes(), metadata.getDependencies(), metadata.getId());

        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();
        int configurationsCount = decoder.readSmallInt();
        Map<String, ConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(configurationsCount);

        for (int i = 0; i < configurationsCount; i++) {
            String configurationName = decoder.readString();
            boolean transitive = true;
            boolean visible = true;
            ImmutableSet<String> hierarchy = ImmutableSet.of(configurationName);
            ImmutableList<ExcludeMetadata> excludes;

            Configuration configuration = configurationDefinitions.get(configurationName);
            if (configuration != null) { // if the configuration represents a variant added by a rule, it is not in the definition list
                transitive = configuration.isTransitive();
                visible = configuration.isVisible();
                hierarchy = LazyToRealisedModuleComponentResolveMetadataHelper.constructHierarchy(configuration, configurationDefinitions);
                excludes = configurationHelper.filterExcludes(hierarchy);
            } else {
                excludes = ImmutableList.of();
            }

            ImmutableAttributes attributes = getAttributeContainerSerializer().read(decoder);
            ImmutableCapabilities capabilities = readCapabilities(decoder);
            boolean hasExplicitExcludes = decoder.readBoolean();
            if (hasExplicitExcludes) {
                excludes = ImmutableList.copyOf(readMavenExcludes(decoder));
            }
            ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = readFiles(decoder, metadata.getId());

            RealisedConfigurationMetadata configurationMetadata = new RealisedConfigurationMetadata(metadata.getId(), configurationName, transitive, visible,
                hierarchy, artifacts, excludes, attributes, capabilities, false, false);

            ImmutableList.Builder<ModuleDependencyMetadata> builder = ImmutableList.builder();
            int dependenciesCount = decoder.readSmallInt();
            for (int j = 0; j < dependenciesCount; j++) {
                byte dependencyType = decoder.readByte();
                switch(dependencyType) {
                    case GRADLE_DEPENDENCY_METADATA:
                        builder.add(readDependencyMetadata(decoder));
                        break;
                    case IVY_DEPENDENCY_METADATA:
                        IvyDependencyDescriptor ivyDependency = readIvyDependency(decoder);
                        ModuleDependencyMetadata dependencyMetadata = configurationHelper.contextualize(configurationMetadata, metadata.getId(), ivyDependency);
                        builder.add(dependencyMetadata.withReason(decoder.readNullableString()));
                        break;
                    case MAVEN_DEPENDENCY_METADATA:
                        throw new IllegalStateException("Unexpected Maven dependency for Ivy module");
                    default:
                        throw new IllegalStateException("Unknown dependency type " + dependencyType);
                }
            }
            ImmutableList<ModuleDependencyMetadata> dependencies = builder.build();
            configurationMetadata.setDependencies(dependencies);

            configurations.put(configurationName, configurationMetadata);
        }
        return configurations;
    }

    private IvyDependencyDescriptor readIvyDependency(Decoder decoder) throws IOException {
        ModuleComponentSelector requested = getComponentSelectorSerializer().read(decoder);
        SetMultimap<String, String> configMappings = readDependencyConfigurationMapping(decoder);
        List<Artifact> artifacts = readDependencyArtifactDescriptors(decoder);
        List<Exclude> excludes = readDependencyExcludes(decoder);
        String dynamicConstraintVersion = decoder.readString();
        boolean changing = decoder.readBoolean();
        boolean transitive = decoder.readBoolean();
        boolean optional = decoder.readBoolean();
        return new IvyDependencyDescriptor(requested, dynamicConstraintVersion, changing, transitive,  optional, configMappings, artifacts, excludes);
    }

    private void writeIvyDependency(Encoder encoder, IvyDependencyDescriptor ivyDependency, String configurationName, boolean configurationAddedByRule) throws IOException {
        getComponentSelectorSerializer().write(encoder, ivyDependency.getSelector());
        writeDependencyConfigurationMapping(encoder, ivyDependency, configurationName, configurationAddedByRule);
        writeArtifacts(encoder, ivyDependency.getDependencyArtifacts());
        writeExcludeRules(encoder, ivyDependency.getAllExcludes());
        encoder.writeString(ivyDependency.getDynamicConstraintVersion());
        encoder.writeBoolean(ivyDependency.isChanging());
        encoder.writeBoolean(ivyDependency.isTransitive());
        encoder.writeBoolean(ivyDependency.isOptional());
    }

    private void writeExcludeRules(Encoder encoder, List<Exclude> excludes) throws IOException {
        encoder.writeSmallInt(excludes.size());
        for (Exclude exclude : excludes) {
            encoder.writeString(exclude.getModuleId().getGroup());
            encoder.writeString(exclude.getModuleId().getName());
            IvyArtifactName artifact = exclude.getArtifact();
            writeNullableArtifact(encoder, artifact);
            writeStringSet(encoder, exclude.getConfigurations());
            encoder.writeNullableString(exclude.getMatcher());
        }
    }

    private void writeArtifacts(Encoder encoder, List<Artifact> artifacts) throws IOException {
        encoder.writeSmallInt(artifacts.size());
        for (Artifact artifact : artifacts) {
            IvyArtifactName artifactName = artifact.getArtifactName();
            encoder.writeString(artifactName.getName());
            encoder.writeString(artifactName.getType());
            encoder.writeNullableString(artifactName.getExtension());
            encoder.writeNullableString(artifactName.getClassifier());
            writeStringSet(encoder, artifact.getConfigurations());
        }
    }

    private SetMultimap<String, String> readDependencyConfigurationMapping(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        SetMultimap<String, String> result = LinkedHashMultimap.create();
        for (int i = 0; i < size; i++) {
            String from = decoder.readString();
            Set<String> to = readStringSet(decoder);
            result.putAll(from, to);
        }
        return result;
    }

    private void writeDependencyConfigurationMapping(Encoder encoder, IvyDependencyDescriptor dep, String configurationName, boolean configurationAddedByRule) throws IOException {
        SetMultimap<String, String> confMappings = dep.getConfMappings();
        int mappingCount = confMappings.keySet().size() + (configurationAddedByRule ? 1 : 0);
        encoder.writeSmallInt(mappingCount);
        for (String conf : confMappings.keySet()) {
            encoder.writeString(conf);
            writeStringSet(encoder, confMappings.get(conf));
        }
        if (configurationAddedByRule) {
            // since the dependencies are reconstructed from the serialized form which interprets the mappings,
            // we have to make sure to also map from the new configuration.
            encoder.writeString(configurationName);
            writeStringSet(encoder, ImmutableSet.copyOf(confMappings.values()));
        }
    }

    private List<Artifact> readDependencyArtifactDescriptors(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        List<Artifact> result = Lists.newArrayListWithCapacity(size);
        for (int i = 0; i < size; i++) {
            IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(decoder.readString(), decoder.readString(), decoder.readNullableString(), decoder.readNullableString());
            result.add(new Artifact(ivyArtifactName, readStringSet(decoder)));
        }
        return result;
    }

    private List<Exclude> readDependencyExcludes(Decoder decoder) throws IOException {
        int len = decoder.readSmallInt();
        List<Exclude> result = Lists.newArrayListWithCapacity(len);
        for (int i = 0; i < len; i++) {
            DefaultExclude rule = readExcludeRule(decoder);
            result.add(rule);
        }
        return result;
    }

}
