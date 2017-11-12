/*
 * Copyright 2013 the original author or authors.
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
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.List;

public interface MutableModuleComponentResolveMetadata {
    /**
     * The identifier for this component
     */
    ModuleComponentIdentifier getComponentId();

    /**
     * The module version associated with this module.
     */
    ModuleVersionIdentifier getId();

    /**
     * Creates an immutable copy of this meta-data.
     */
    ModuleComponentResolveMetadata asImmutable();

    /**
     * Sets the component id and legacy module version id
     */
    void setComponentId(ModuleComponentIdentifier componentId);

    /**
     * Returns the hash of the resource(s) from which this metadata was created.
     */
    HashValue getContentHash();
    void setContentHash(HashValue hash);

    boolean isMissing();
    void setMissing(boolean missing);

    boolean isChanging();
    void setChanging(boolean changing);

    String getStatus();
    void setStatus(String status);

    List<String> getStatusScheme();
    void setStatusScheme(List<String> statusScheme);

    ModuleSource getSource();
    void setSource(ModuleSource source);

    /**
     * Return the configurations of this component.
     */
    ImmutableMap<String, ? extends ConfigurationMetadata> getConfigurations();

    /**
     * Checks if the metadata defines the given variant. Depending on the origin of the metadata, a "variant" can be backed
     * by another concept (for example an ivy configuration). The check should be implemented in a cheap way without creating
     * full variant/configuration metadata objects since the method only needs to check the name.
     */
    boolean definesVariant(String name);

    /**
     * Returns the dependency declarations of this component.
     */
    List<? extends ModuleDependencyMetadata> getDependencies();

    /**
     * Replaces the dependencies of this module version.
     */
    void setDependencies(Iterable<? extends ModuleDependencyMetadata> dependencies);

    /**
     * Returns the artifacts to apply to all configurations. Is null when there are no such artifacts.
     */
    @Nullable
    ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactOverrides();

    /**
     * Replaces the artifacts of this module version. The artifacts are attached to all configurations.
     */
    void setArtifactOverrides(Iterable<? extends ModuleComponentArtifactMetadata> artifacts);

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier);

    void addDependencyMetadataRule(String name, Action<DependenciesMetadata> action, Instantiator instantiator, NotationParser<Object, org.gradle.api.artifacts.DependencyMetadata> dependencyNotationParser);
}
