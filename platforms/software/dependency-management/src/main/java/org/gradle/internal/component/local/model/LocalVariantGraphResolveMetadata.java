/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.Transformer;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Graph metadata for local variants.
 */
public interface LocalVariantGraphResolveMetadata extends VariantGraphResolveMetadata {

    /**
     * Get the configuration name that this variant is identified by, if any.
     */
    @Nullable
    String getConfigurationName();

    /**
     * Returns the files attached to this variant, if any.
     * These should be represented as dependencies, but are currently represented as files as a migration step.
     */
    Set<LocalFileDependencyMetadata> getFiles();

    /**
     * Calculates the set of artifacts for this variant.
     *
     * <p>Note that this may be expensive, and should be called only when required.</p>
     */
    // TODO: This should be part of a state object, not a metadata object
    LocalVariantArtifactGraphResolveMetadata prepareToResolveArtifacts();

    /**
     * Returns a copy of this variant metadata, except with all artifacts transformed by the given transformer.
     *
     * @param artifactTransformer A transformer applied to all artifacts and sub-variant artifacts.
     *
     * @return A copy of this metadata, with the given transformer applied to all artifacts.
     */
    LocalVariantGraphResolveMetadata copyWithTransformedArtifacts(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer);

}
