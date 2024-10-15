/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.util.List;

/**
 * State for a variant of a component, intended for use during graph resolution.
 * <p>
 * This state type manages expensive operations required to resolve a variant. These include
 * managing dependencies and artifacts, which may not be easily available from the metadata.
 */
public interface VariantGraphResolveState extends HasAttributes {

    /**
     * A unique id for this variant within the current build tree. Note that this id is not stable across Gradle invocations.
     */
    long getInstanceId();

    String getName();

    @Override
    ImmutableAttributes getAttributes();

    ImmutableCapabilities getCapabilities();

    VariantGraphResolveMetadata getMetadata();

    /**
     * Get the dependencies of this variant.
     */
    List<? extends DependencyMetadata> getDependencies();

    /**
     * Get the exclusions to apply to the dependencies and artifacts of this variant.
     */
    List<? extends ExcludeMetadata> getExcludes();

    /**
     * Returns the state required to select and resolve artifacts for this variant. Does not
     * necessarily download the artifacts.
     * <p>
     * Note that this may be expensive, for example it may block waiting for access to the source
     * project or for network or IO requests to the source repository, and should be used only
     * when required.
     */
    VariantArtifactResolveState prepareForArtifactResolution();
}
