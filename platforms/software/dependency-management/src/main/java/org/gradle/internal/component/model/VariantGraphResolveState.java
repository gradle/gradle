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

import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;

/**
 * State for a component variant that is used to perform dependency graph resolution.
 *
 * <p>This does not include any information about the artifacts of the variant, which are generally not required during graph resolution.</p>
 */
public interface VariantGraphResolveState extends HasAttributes {
    /**
     * A unique id for this variant within the current build tree. Note that this id is not stable across Gradle invocations.
     */
    long getInstanceId();

    String getName();

    ImmutableAttributes getAttributes();

    CapabilitiesMetadata getCapabilities();

    VariantGraphResolveMetadata getMetadata();

    /**
     * Does this instance represent some temporary or mutated view of the variant?
     *
     * See {@link ComponentGraphResolveState#isAdHoc()} for a definition of "ad hoc".
     */
    boolean isAdHoc();

    /**
     * Returns the public view for this variant.
     */
    ResolvedVariantResult getVariantResult(@Nullable ResolvedVariantResult externalVariant);

    /**
     * Determines the set of artifacts for this variant, if required during graph resolution. Does not necessarily download the artifacts.
     *
     * <p>Note that this may be expensive, for example it may block waiting for access to the source project or for network or IO requests to the source repository, and should be used only when
     * required.
     */
    VariantArtifactGraphResolveMetadata resolveArtifacts();

    /**
     * Returns the state required to select and resolve artifacts for this variant.
     */
    VariantArtifactResolveState prepareForArtifactResolution();
}
