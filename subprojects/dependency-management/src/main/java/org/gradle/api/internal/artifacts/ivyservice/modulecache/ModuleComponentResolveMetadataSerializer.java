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

package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.external.model.AbstractLazyModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.DefaultIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.IvyConfigurationHelper;
import org.gradle.internal.component.external.model.IvyDependencyDescriptor;
import org.gradle.internal.component.external.model.MavenDependencyDescriptor;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.RealisedConfigurationMetadata;
import org.gradle.internal.component.external.model.RealisedIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.RealisedMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.caching.FullAttributeContainerSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.EOFException;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleComponentResolveMetadataSerializer extends AbstractSerializer<ModuleComponentResolveMetadata> {

    private static final byte GRADLE_DEPENDENCY_METADATA = 1;
    private static final byte MAVEN_DEPENDENCY_METADATA = 2;
    private static final byte IVY_DEPENDENCY_METADATA = 3;
    public static final String COMPILE_DERIVED_VARIANT_NAME = "compile___derived";
    public static final String RUNTIME_DERIVED_VARIANT_NAME = "runtime___derived";

    private final ModuleMetadataSerializer delegate;
    private final FullAttributeContainerSerializer attributeContainerSerializer;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ModuleComponentSelectorSerializer componentSelectorSerializer;
    private final DefaultExcludeRuleConverter excludeRuleConverter;

    public ModuleComponentResolveMetadataSerializer(ModuleMetadataSerializer delegate, FullAttributeContainerSerializer attributeContainerSerializer, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.delegate = delegate;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.componentSelectorSerializer = new ModuleComponentSelectorSerializer(attributeContainerSerializer);
        this.excludeRuleConverter = new DefaultExcludeRuleConverter(moduleIdentifierFactory);
    }

    @Override
    public ModuleComponentResolveMetadata read(Decoder decoder) throws EOFException, Exception {

        AbstractLazyModuleComponentResolveMetadata resolveMetadata = (AbstractLazyModuleComponentResolveMetadata) delegate.read(decoder, moduleIdentifierFactory).asImmutable();
        Map<String, List<GradleDependencyMetadata>> variantToDependencies = readVariantDependencies(decoder);
        ImmutableList<? extends ComponentVariant> variants = resolveMetadata.getVariants();
        ImmutableList.Builder<ImmutableRealisedVariantImpl> builder = ImmutableList.builder();
        for (ComponentVariant variant: variants) {
            builder.add(new ImmutableRealisedVariantImpl(resolveMetadata.getId(), variant.getName(), variant.getAttributes().asImmutable(), variant.getDependencies(), variant.getDependencyConstraints(),
                variant.getFiles(), ImmutableCapabilities.of(variant.getCapabilities().getCapabilities()), variantToDependencies.get(variant.getName())));
        }
        ImmutableList<ImmutableRealisedVariantImpl> realisedVariants = builder.build();

        if (resolveMetadata instanceof DefaultIvyModuleResolveMetadata) {
            return readIvyMetadata(decoder, (DefaultIvyModuleResolveMetadata) resolveMetadata, realisedVariants);
        } else if (resolveMetadata instanceof DefaultMavenModuleResolveMetadata) {
            return readMavenMetadata(decoder, (DefaultMavenModuleResolveMetadata) resolveMetadata, realisedVariants);
        } else {
            throw new IllegalStateException("Unknown resolved metadata type: " + resolveMetadata.getClass());
        }
    }

    private ModuleComponentResolveMetadata readMavenMetadata(Decoder decoder, DefaultMavenModuleResolveMetadata resolveMetadata, ImmutableList<ImmutableRealisedVariantImpl> realisedVariants) throws IOException {
        Map<String, ConfigurationMetadata> configurations = readMavenConfigurationsAndDerivedVariants(decoder, resolveMetadata);
        List<ConfigurationMetadata> derivedVariants = Lists.newArrayListWithCapacity(2);
        addDerivedVariant(configurations, derivedVariants, COMPILE_DERIVED_VARIANT_NAME);
        addDerivedVariant(configurations, derivedVariants, RUNTIME_DERIVED_VARIANT_NAME);
        return new RealisedMavenModuleResolveMetadata(resolveMetadata, realisedVariants, derivedVariants, configurations);
    }

    private void addDerivedVariant(Map<String, ConfigurationMetadata> configurations, List<ConfigurationMetadata> derivedVariants, String name) {
        ConfigurationMetadata configurationMetadata = configurations.remove(name);
        if (configurationMetadata != null) {
            derivedVariants.add(configurationMetadata);
        }
    }

    private Map<String, ConfigurationMetadata> readMavenConfigurationsAndDerivedVariants(Decoder decoder, DefaultMavenModuleResolveMetadata metadata) throws IOException {
        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();

        boolean derivedVariants = decoder.readBoolean();
        int configurationsCount = decoder.readSmallInt();
        Map<String, ConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(configurationsCount);
        for (int i = 0; i < configurationsCount; i++) {
            String configurationName = decoder.readString();
            Configuration configuration = configurationDefinitions.get(configurationName);
            ImmutableList<String> hierarchy = AbstractRealisedModuleComponentResolveMetadata.constructHierarchy(configuration, configurationDefinitions);
            ImmutableAttributes attributes = attributeContainerSerializer.read(decoder);
            ImmutableCapabilities capabilities = readCapabilities(decoder);

            RealisedConfigurationMetadata configurationMetadata = new RealisedConfigurationMetadata(metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(),
                hierarchy, RealisedMavenModuleResolveMetadata.getArtifactsForConfiguration(metadata.getId(), configurationName), ImmutableList.<ExcludeMetadata>of(), attributes, capabilities);
            ImmutableList.Builder<ModuleDependencyMetadata> builder = ImmutableList.builder();
            int dependenciesCount = decoder.readSmallInt();
            for (int j = 0; j < dependenciesCount; j++) {
                byte dependencyType = decoder.readByte();
                switch(dependencyType) {
                    case GRADLE_DEPENDENCY_METADATA:
                        builder.add(readDependencyMetadata(decoder));
                        break;
                    case MAVEN_DEPENDENCY_METADATA:
                        MavenDependencyDescriptor mavenDependencyDescriptor = readMavenDependency(decoder);
                        ModuleDependencyMetadata dependencyMetadata = RealisedMavenModuleResolveMetadata.contextualize(configurationMetadata, metadata.getId(), mavenDependencyDescriptor, metadata.isImprovedPomSupportEnabled());
                        builder.add(dependencyMetadata.withReason(decoder.readNullableString()));
                        break;
                    case IVY_DEPENDENCY_METADATA:
                        throw new IllegalStateException("Unexpected Ivy dependency for Maven module");
                    default:
                        throw new IllegalStateException("Unknown dependency type " + dependencyType);
                }
            }
            ImmutableList<ModuleDependencyMetadata> dependencies = builder.build();
            configurationMetadata.setDependencies(dependencies);

            configurations.put(configurationName, configurationMetadata);
            if (derivedVariants) {
                if (configurationName.equals("compile")) {
                    ConfigurationMetadata compileDerivedVariant = RealisedMavenModuleResolveMetadata.withUsageAttribute(configurationMetadata, Usage.JAVA_API, metadata.getAttributesFactory(), attributes, metadata.getObjectInstantiator());
                    configurations.put(COMPILE_DERIVED_VARIANT_NAME, compileDerivedVariant);
                } else if (configurationName.equals("runtime")) {
                    ConfigurationMetadata runtimeDerivedVariant = RealisedMavenModuleResolveMetadata.withUsageAttribute(configurationMetadata, Usage.JAVA_RUNTIME, metadata.getAttributesFactory(), attributes, metadata.getObjectInstantiator());
                    configurations.put(RUNTIME_DERIVED_VARIANT_NAME, runtimeDerivedVariant);
                }
            }
        }
        return configurations;
    }

    @Override
    public void write(Encoder encoder, ModuleComponentResolveMetadata value) throws Exception {
        AbstractRealisedModuleComponentResolveMetadata transformed = transformToRealisedForSerialization(value);
        delegate.write(encoder, transformed);
        writeRealisedVariantsData(encoder, transformed);
        writeRealisedConfigurationsData(encoder, transformed);
    }

    private void writeDerivedVariants(Encoder encoder, RealisedMavenModuleResolveMetadata metadata) throws IOException {
        encoder.writeBoolean(!metadata.getDerivedVariants().isEmpty());
    }

    private AbstractRealisedModuleComponentResolveMetadata transformToRealisedForSerialization(ModuleComponentResolveMetadata metadata) {
        if (metadata instanceof AbstractRealisedModuleComponentResolveMetadata) {
            return (AbstractRealisedModuleComponentResolveMetadata) metadata;
        } else if (metadata instanceof DefaultIvyModuleResolveMetadata) {
            return RealisedIvyModuleResolveMetadata.transform((DefaultIvyModuleResolveMetadata) metadata);
        } else if (metadata instanceof DefaultMavenModuleResolveMetadata) {
            return RealisedMavenModuleResolveMetadata.transform((DefaultMavenModuleResolveMetadata) metadata);
        }
        throw new IllegalStateException("The type of metadata received is not supported - " + metadata.getClass().getName());
    }

    private ModuleComponentResolveMetadata readIvyMetadata(Decoder decoder, DefaultIvyModuleResolveMetadata resolveMetadata, ImmutableList<ImmutableRealisedVariantImpl> realisedVariants) throws IOException {
        return new RealisedIvyModuleResolveMetadata(resolveMetadata, realisedVariants, readIvyConfigurations(decoder, resolveMetadata));
    }

    private Map<String, ConfigurationMetadata> readIvyConfigurations(Decoder decoder, DefaultIvyModuleResolveMetadata metadata) throws IOException {
        Map<Artifact, ModuleComponentArtifactMetadata> artifacts = new IdentityHashMap<Artifact, ModuleComponentArtifactMetadata>();
        IvyConfigurationHelper configurationHelper = new IvyConfigurationHelper(metadata.getArtifactDefinitions(), artifacts, metadata.getExcludes(), metadata.getDependencies(), metadata.getId());

        ImmutableMap<String, Configuration> configurationDefinitions = metadata.getConfigurationDefinitions();

        int configurationsCount = decoder.readSmallInt();
        Map<String, ConfigurationMetadata> configurations = Maps.newHashMapWithExpectedSize(configurationsCount);
        for (int i = 0; i < configurationsCount; i++) {
            String configurationName = decoder.readString();
            Configuration configuration = configurationDefinitions.get(configurationName);
            assert configuration != null;
            ImmutableList<String> hierarchy = AbstractRealisedModuleComponentResolveMetadata.constructHierarchy(configuration, configurationDefinitions);
            ImmutableAttributes attributes = attributeContainerSerializer.read(decoder);
            ImmutableCapabilities capabilities = readCapabilities(decoder);

            RealisedConfigurationMetadata configurationMetadata = new RealisedConfigurationMetadata(metadata.getId(), configurationName, configuration.isTransitive(), configuration.isVisible(),
                hierarchy, configurationHelper.filterArtifacts(configurationName, hierarchy), configurationHelper.filterExcludes(hierarchy), attributes, capabilities);

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

    private Map<String, List<GradleDependencyMetadata>> readVariantDependencies(Decoder decoder) throws IOException {
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

    private void writeRealisedVariantsData(Encoder encoder, AbstractRealisedModuleComponentResolveMetadata transformed) throws IOException {
        encoder.writeSmallInt(transformed.getVariants().size());
        for (ComponentVariant variant: transformed.getVariants()) {
            if (variant instanceof ImmutableRealisedVariantImpl) {
                ImmutableRealisedVariantImpl realisedVariant = (ImmutableRealisedVariantImpl) variant;
                encoder.writeString(realisedVariant.getName());
                encoder.writeSmallInt(realisedVariant.getDependencyMetadata().size());
                for (GradleDependencyMetadata dependencyMetadata: realisedVariant.getDependencyMetadata()) {
                    writeDependencyMetadata(encoder, dependencyMetadata);
                }
            } else {
                throw new IllegalStateException("Unknown type of variant: " + variant.getClass());
            }
        }
    }

    private void writeDependencyMetadata(Encoder encoder, GradleDependencyMetadata dependencyMetadata) throws IOException {
        componentSelectorSerializer.write(encoder, dependencyMetadata.getSelector());
        List<ExcludeMetadata> excludes = dependencyMetadata.getExcludes();
        writeMavenExcludeRules(encoder, excludes);
        encoder.writeBoolean(dependencyMetadata.isPending());
        encoder.writeNullableString(dependencyMetadata.getReason());
    }

    private GradleDependencyMetadata readDependencyMetadata(Decoder decoder) throws IOException {
        ModuleComponentSelector selector = componentSelectorSerializer.read(decoder);
        List<ExcludeMetadata> excludes = readMavenExcludes(decoder);
        boolean pending = decoder.readBoolean();
        String reason = decoder.readNullableString();
        return new GradleDependencyMetadata(selector, excludes, pending, reason);
    }

    private List<ExcludeMetadata> readMavenExcludes(Decoder decoder) throws IOException {
        int excludeCount = decoder.readSmallInt();
        List<ExcludeMetadata> excludes = Lists.newArrayListWithCapacity(excludeCount);
        for (int i = 0; i < excludeCount; i++) {
            String group = decoder.readString();
            String name = decoder.readString();
            excludes.add(excludeRuleConverter.createExcludeRule(group, name));
        }
        return excludes;
    }

    private void writeRealisedConfigurationsData(Encoder encoder, AbstractRealisedModuleComponentResolveMetadata transformed) throws IOException {
        if (transformed instanceof RealisedMavenModuleResolveMetadata) {
            writeDerivedVariants(encoder, (RealisedMavenModuleResolveMetadata) transformed);
        }
        encoder.writeSmallInt(transformed.getConfigurationNames().size());
        for (String configurationName: transformed.getConfigurationNames()) {
            ConfigurationMetadata configuration = transformed.getConfiguration(configurationName);
            assert configuration != null;
            encoder.writeString(configurationName);
            attributeContainerSerializer.write(encoder, configuration.getAttributes());
            writeCapabilities(encoder, configuration.getCapabilities().getCapabilities());
            List<? extends DependencyMetadata> dependencies = configuration.getDependencies();
            encoder.writeSmallInt(dependencies.size());
            for (DependencyMetadata dependency: dependencies) {
                if (dependency instanceof GradleDependencyMetadata) {
                    encoder.writeByte(GRADLE_DEPENDENCY_METADATA);
                    writeDependencyMetadata(encoder, (GradleDependencyMetadata) dependency);
                } else if (dependency instanceof ConfigurationBoundExternalDependencyMetadata) {
                    ConfigurationBoundExternalDependencyMetadata dependencyMetadata = (ConfigurationBoundExternalDependencyMetadata) dependency;
                    ExternalDependencyDescriptor dependencyDescriptor = dependencyMetadata.getDependencyDescriptor();
                    if (dependencyDescriptor instanceof MavenDependencyDescriptor) {
                        encoder.writeByte(MAVEN_DEPENDENCY_METADATA);
                        writeMavenDependency(encoder, (MavenDependencyDescriptor) dependencyDescriptor);
                    } else if (dependencyDescriptor instanceof IvyDependencyDescriptor) {
                        encoder.writeByte(IVY_DEPENDENCY_METADATA);
                        writeIvyDependency(encoder, (IvyDependencyDescriptor) dependencyDescriptor);
                    } else {
                        throw new IllegalStateException("Unknown type of dependency descriptor: " + dependencyDescriptor.getClass());
                    }
                    encoder.writeNullableString(dependency.getReason());
                }
            }
        }
    }

    private void writeCapabilities(Encoder encoder, List<? extends Capability> capabilities) throws IOException {
        encoder.writeSmallInt(capabilities.size());
        for (Capability capability: capabilities) {
            encoder.writeString(capability.getGroup());
            encoder.writeString(capability.getName());
            encoder.writeString(capability.getVersion());
        }
    }

    private ImmutableCapabilities readCapabilities(Decoder decoder) throws IOException {
        int capabilitiesCount = decoder.readSmallInt();
        List<Capability> rawCapabilities = Lists.newArrayListWithCapacity(capabilitiesCount);
        for (int j = 0; j < capabilitiesCount; j++) {
            rawCapabilities.add(new ImmutableCapability(decoder.readString(), decoder.readString(), decoder.readString()));
        }
        return ImmutableCapabilities.of(rawCapabilities);
    }

    private MavenDependencyDescriptor readMavenDependency(Decoder decoder) throws IOException {
        ModuleComponentSelector requested = componentSelectorSerializer.read(decoder);
        IvyArtifactName artifactName = readNullableArtifact(decoder);
        List<ExcludeMetadata> mavenExcludes = readMavenExcludes(decoder);
        MavenScope scope = MavenScope.values()[decoder.readSmallInt()];
        boolean optional = decoder.readBoolean();
        return new MavenDependencyDescriptor(scope, optional, requested, artifactName, mavenExcludes);
    }

    private IvyArtifactName readNullableArtifact(Decoder decoder) throws IOException {
        boolean hasArtifact = decoder.readBoolean();
        IvyArtifactName artifactName = null;
        if (hasArtifact) {
            String artifact = decoder.readString();
            String type = decoder.readString();
            String ext = decoder.readNullableString();
            String classifier = decoder.readNullableString();
            artifactName = new DefaultIvyArtifactName(artifact, type, ext, classifier);
        }
        return artifactName;
    }

    private void writeMavenDependency(Encoder encoder, MavenDependencyDescriptor mavenDependency) throws IOException {
        componentSelectorSerializer.write(encoder, mavenDependency.getSelector());
        writeNullableArtifact(encoder, mavenDependency.getDependencyArtifact());
        writeMavenExcludeRules(encoder, mavenDependency.getAllExcludes());
        encoder.writeSmallInt(mavenDependency.getScope().ordinal());
        encoder.writeBoolean(mavenDependency.isOptional());
    }

    private void writeNullableArtifact(Encoder encoder, IvyArtifactName artifact) throws IOException {
        if (artifact == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            encoder.writeString(artifact.getName());
            encoder.writeString(artifact.getType());
            encoder.writeNullableString(artifact.getExtension());
            encoder.writeNullableString(artifact.getClassifier());
        }
    }

    private void writeMavenExcludeRules(Encoder encoder, List<ExcludeMetadata> excludes) throws IOException {
        encoder.writeSmallInt(excludes.size());
        for (ExcludeMetadata exclude : excludes) {
            encoder.writeString(exclude.getModuleId().getGroup());
            encoder.writeString(exclude.getModuleId().getName());
        }
    }

    private IvyDependencyDescriptor readIvyDependency(Decoder decoder) throws IOException {
        ModuleComponentSelector requested = componentSelectorSerializer.read(decoder);
        SetMultimap<String, String> configMappings = readDependencyConfigurationMapping(decoder);
        List<Artifact> artifacts = readDependencyArtifactDescriptors(decoder);
        List<Exclude> excludes = readDependencyExcludes(decoder);
        String dynamicConstraintVersion = decoder.readString();
        boolean changing = decoder.readBoolean();
        boolean transitive = decoder.readBoolean();
        boolean optional = decoder.readBoolean();
        return new IvyDependencyDescriptor(requested, dynamicConstraintVersion, changing, transitive,  optional, configMappings, artifacts, excludes);
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

    private DefaultExclude readExcludeRule(Decoder decoder) throws IOException {
        String moduleOrg = decoder.readString();
        String moduleName = decoder.readString();
        IvyArtifactName artifactName = readNullableArtifact(decoder);
        String[] confs = readStringSet(decoder).toArray(new String[0]);
        String matcher = decoder.readNullableString();
        return new DefaultExclude(moduleIdentifierFactory.module(moduleOrg, moduleName), artifactName, confs, matcher);
    }

    private void writeIvyDependency(Encoder encoder, IvyDependencyDescriptor ivyDependency) throws IOException {
        componentSelectorSerializer.write(encoder, ivyDependency.getSelector());
        writeDependencyConfigurationMapping(encoder, ivyDependency);
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

    private void writeDependencyConfigurationMapping(Encoder encoder, IvyDependencyDescriptor dep) throws IOException {
        SetMultimap<String, String> confMappings = dep.getConfMappings();
        encoder.writeSmallInt(confMappings.keySet().size());
        for (String conf : confMappings.keySet()) {
            encoder.writeString(conf);
            writeStringSet(encoder, confMappings.get(conf));
        }
    }

    private void writeStringSet(Encoder encoder, Set<String> values) throws IOException {
        encoder.writeSmallInt(values.size());
        for (String configuration : values) {
            encoder.writeString(configuration);
        }
    }

    private Set<String> readStringSet(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        Set<String> set = new LinkedHashSet<String>(3 * size / 2, 0.9f);
        for (int i = 0; i < size; i++) {
            set.add(decoder.readString());
        }
        return set;
    }
}
