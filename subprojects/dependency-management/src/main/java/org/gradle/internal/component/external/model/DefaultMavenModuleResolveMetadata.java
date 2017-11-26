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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public class DefaultMavenModuleResolveMetadata extends AbstractModuleComponentResolveMetadata<MavenConfigurationMetadata> implements MavenModuleResolveMetadata {

    public static final String POM_PACKAGING = "pom";
    public static final Collection<String> JAR_PACKAGINGS = Arrays.asList("jar", "ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private static final PreferJavaRuntimeVariant SCHEMA_DEFAULT_JAVA_VARIANTS = PreferJavaRuntimeVariant.schema();

    private final String packaging;
    private final boolean relocated;
    private final String snapshotTimestamp;

    DefaultMavenModuleResolveMetadata(DefaultMutableMavenModuleResolveMetadata metadata) {
        super(metadata);
        packaging = metadata.getPackaging();
        relocated = metadata.isRelocated();
        snapshotTimestamp = metadata.getSnapshotTimestamp();
    }

    private DefaultMavenModuleResolveMetadata(DefaultMavenModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        packaging = metadata.packaging;
        relocated = metadata.relocated;
        snapshotTimestamp = metadata.snapshotTimestamp;
    }

    @Override
    protected MavenConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> parents, DependencyMetadataRules dependencyMetadataRules) {
        ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts = getArtifactsForConfiguration(name);
        MavenConfigurationMetadata mavenConfigurationMetadata = new MavenConfigurationMetadata(componentId, name, transitive, visible, parents, artifacts, dependencyMetadataRules);
        mavenConfigurationMetadata.setDependencies(filterDependencies(mavenConfigurationMetadata));
        return mavenConfigurationMetadata;
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

    private ImmutableList<ModuleDependencyMetadata> filterDependencies(MavenConfigurationMetadata config) {
        ImmutableList.Builder<ModuleDependencyMetadata> filteredDependencies = ImmutableList.builder();
        for (ModuleDependencyMetadata dependency : dependencies) {
            DefaultDependencyMetadata defaultDependencyMetadata = (DefaultDependencyMetadata) dependency;
            if (include(defaultDependencyMetadata, config.getHierarchy())) {
                filteredDependencies.add(contextualize(config, getComponentId(), defaultDependencyMetadata));
            }
        }
        return filteredDependencies.build();
    }

    private ModuleDependencyMetadata contextualize(ConfigurationMetadata config, ModuleComponentIdentifier componentId, DefaultDependencyMetadata incoming) {
        return new ConfigurationDependencyMetadataWrapper(config, componentId, incoming);
    }

    private boolean include(DefaultDependencyMetadata dependency, Collection<String> hierarchy) {
        for (String moduleConfiguration : dependency.getModuleConfigurations()) {
            if (hierarchy.contains(moduleConfiguration)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DefaultMavenModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultMavenModuleResolveMetadata(this, source);
    }

    @Override
    public MutableMavenModuleResolveMetadata asMutable() {
        return new DefaultMutableMavenModuleResolveMetadata(this);
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

    @Nullable
    public String getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    @Nullable
    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return SCHEMA_DEFAULT_JAVA_VARIANTS;
    }

}
