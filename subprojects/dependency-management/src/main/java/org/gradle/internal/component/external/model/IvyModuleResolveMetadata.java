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
import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;

/**
 * Meta-data for a component resolved from an Ivy repository.
 */
public interface IvyModuleResolveMetadata extends ModuleComponentResolveMetadata {
    /**
     * {@inheritDoc}
     */
    @Override
    MutableIvyModuleResolveMetadata asMutable();

    /**
     * {@inheritDoc}
     */
    @Override
    IvyModuleResolveMetadata withSource(ModuleSource source);

    /***
     * Returns the branch attribute for the module.
     *
     * @return the branch attribute for the module
     */
    @Nullable
    String getBranch();

    /**
     * Returns the Ivy definitions for the configurations of this module.
     */
    ImmutableMap<String, Configuration> getConfigurationDefinitions();

    /**
     * Returns the Ivy definitions for artifacts of this module.
     */
    ImmutableList<Artifact> getArtifactDefinitions();

    /**
     * Returns the Ivy excludes for this module.
     */
    ImmutableList<Exclude> getExcludes();

    /**
     * Returns the extra info for the module.
     *
     * @return the extra info for the module
     */
    ImmutableMap<NamespaceId, String> getExtraAttributes();
}
