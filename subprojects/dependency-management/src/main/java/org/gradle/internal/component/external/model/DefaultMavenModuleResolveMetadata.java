/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.CoercingStringValueSnapshot;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.descriptor.MavenScope;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public class DefaultMavenModuleResolveMetadata extends AbstractModuleComponentResolveMetadata implements MavenModuleResolveMetadata {

    public static final String POM_PACKAGING = "pom";
    public static final Collection<String> JAR_PACKAGINGS = Arrays.asList("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private static final PreferJavaRuntimeVariant SCHEMA_DEFAULT_JAVA_VARIANTS = PreferJavaRuntimeVariant.schema();
    // We need to work with the 'String' version of the usage attribute, since this is expected for all providers by the `PreferJavaRuntimeVariant` schema
    private static final Attribute<String> USAGE_ATTRIBUTE = Attribute.of(Usage.USAGE_ATTRIBUTE.getName(), String.class);

    private final ImmutableAttributesFactory attributesFactory;
    private final NamedObjectInstantiator objectInstantiator;
    private final ExperimentalFeatures experimentalFeatures;

    private final ImmutableList<MavenDependencyDescriptor> dependencies;
    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;

    private ImmutableList<? extends ConfigurationMetadata> derivedVariants;

    DefaultMavenModuleResolveMetadata(DefaultMutableMavenModuleResolveMetadata metadata, ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator objectInstantiator, ExperimentalFeatures experimentalFeatures) {
        super(metadata);
        this.experimentalFeatures = experimentalFeatures;
        this.attributesFactory = attributesFactory;
        this.objectInstantiator = objectInstantiator;
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
        dependencies = metadata.getDependencies();
    }

    private DefaultMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.experimentalFeatures = metadata.experimentalFeatures;
        this.attributesFactory = metadata.attributesFactory;
        this.objectInstantiator = metadata.objectInstantiator;
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
        dependencies = metadata.dependencies;

        copyCachedState(metadata);
    }

    @Override
    protected DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> parents, VariantMetadataRules componentMetadataRules) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = getArtifactsForConfiguration(name);
        DefaultConfigurationMetadata configuration = new DefaultConfigurationMetadata(componentId, name, transitive, visible, parents, artifacts, componentMetadataRules, ImmutableList.<ExcludeMetadata>of());
        configuration.setDependencies(filterDependencies(configuration));
        return configuration;
    }

    @Override
    protected ImmutableList<? extends ConfigurationMetadata> maybeDeriveVariants() {
        return isJavaLibrary() ? getDerivedVariants() : ImmutableList.<ConfigurationMetadata>of();
    }

    private ImmutableList<? extends ConfigurationMetadata> getDerivedVariants() {
        if (derivedVariants == null) {
            derivedVariants = ImmutableList.of(
                withUsageAttribute((DefaultConfigurationMetadata) getConfiguration("compile"), Usage.JAVA_API, attributesFactory),
                withUsageAttribute((DefaultConfigurationMetadata) getConfiguration("runtime"), Usage.JAVA_RUNTIME, attributesFactory));
        }
        return derivedVariants;
    }

    private ConfigurationMetadata withUsageAttribute(DefaultConfigurationMetadata conf, String usage, ImmutableAttributesFactory attributesFactory) {
        return conf.withAttributes(attributesFactory.concat(ImmutableAttributes.EMPTY, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, objectInstantiator)));
    }

    private ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactsForConfiguration(String name) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;
        if (name.equals("compile") || name.equals("runtime") || name.equals("default") || name.equals("test")) {
            artifacts = ImmutableList.of(new DefaultModuleComponentArtifactMetadata(getComponentId(), new DefaultIvyArtifactName(getComponentId().getModule(), "jar", "jar")));
        } else {
            artifacts = ImmutableList.of();
        }
        return artifacts;
    }

    private ImmutableList<ModuleDependencyMetadata> filterDependencies(DefaultConfigurationMetadata config) {
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = ImmutableList.builder();
        for (MavenDependencyDescriptor dependency : dependencies) {
            if (include(dependency, config.getName(), config.getHierarchy())) {
                filteredDependencies.add(contextualize(config, getComponentId(), dependency));
            }
        }
        return filteredDependencies.build();
    }

    private ModuleDependencyMetadata contextualize(ConfigurationMetadata config, ModuleComponentIdentifier componentId, MavenDependencyDescriptor incoming) {
        return new ConfigurationDependencyMetadataWrapper(config, componentId, incoming);
    }

    private boolean include(MavenDependencyDescriptor dependency, String configName, Collection<String> hierarchy) {
        MavenScope dependencyScope = dependency.getScope();

        if ("optional".equals(configName)) {
            // Include all 'optional' dependencies in "optional" configuration
            return dependency.isOptional()
                && dependencyScope != MavenScope.Test
                && dependencyScope != MavenScope.System;
        }

        return hierarchy.contains(dependencyScope.name().toLowerCase());
    }

    @Override
    public DefaultMavenModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultMavenModuleResolveMetadata(this, source);
    }

    @Override
    public MutableMavenModuleResolveMetadata asMutable() {
        return new DefaultMutableMavenModuleResolveMetadata(this, attributesFactory, objectInstantiator, experimentalFeatures);
    }

    public String getPackaging() {
        return packaging;
    }

    public boolean isRelocated() {
        return relocated;
    }

    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    public boolean isKnownJarPackaging() {
        return JAR_PACKAGINGS.contains(packaging);
    }

    private boolean isJavaLibrary() {
        return experimentalFeatures.isEnabled() && (isKnownJarPackaging() || isPomPackaging());
    }

    @Nullable
    public String getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    @Nullable
    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return SCHEMA_DEFAULT_JAVA_VARIANTS;
    }

    @Override
    public ImmutableList<MavenDependencyDescriptor> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultMavenModuleResolveMetadata that = (DefaultMavenModuleResolveMetadata) o;
        return relocated == that.relocated
            && Objects.equal(dependencies, that.dependencies)
            && Objects.equal(packaging, that.packaging)
            && Objects.equal(snapshotTimestamp, that.snapshotTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(),
            dependencies,
            packaging,
            relocated,
            snapshotTimestamp);
    }
}
