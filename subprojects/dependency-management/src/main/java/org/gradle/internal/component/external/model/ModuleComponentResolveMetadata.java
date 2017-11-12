/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashValue;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The meta-data for a component that is resolved from a module in a binary repository.
 *
 * <p>Implementations of this type should be immutable and thread safe.</p>
 */
public interface ModuleComponentResolveMetadata extends ComponentResolveMetadata {
    /**
     * {@inheritDoc}
     */
    ModuleComponentIdentifier getComponentId();

    /**
     * {@inheritDoc}
     */
    ModuleComponentResolveMetadata withSource(ModuleSource source);

    /**
     * Creates a mutable copy of this metadata.
     *
     * Note that this method can be expensive. Often it is more efficient to use a more specialised mutation method such as {@link #withSource(ModuleSource)} rather than this method.
     */
    MutableModuleComponentResolveMetadata asMutable();

    /**
     * Return the configurations of this component.
     */
    ImmutableMap<String, ? extends ConfigurationMetadata> getConfigurations();

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier);

    @Nullable
    ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactOverrides();

    /**
     * Returns the hash of the resource(s) from which this metadata was created.
     */
    HashValue getContentHash();

    @Override
    List<? extends ModuleDependencyMetadata> getDependencies();
}
