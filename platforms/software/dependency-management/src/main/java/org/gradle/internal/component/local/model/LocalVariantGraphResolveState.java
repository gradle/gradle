/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.internal.component.model.VariantGraphResolveState;

import java.util.Set;

/**
 * {@link VariantGraphResolveState} for variants of local components.
 */
public interface LocalVariantGraphResolveState extends VariantGraphResolveState {

    @Override
    LocalVariantGraphResolveMetadata getMetadata();

    /**
     * Returns the file dependencies attached to this variant, if any.
     * <p>
     * These should be represented as dependencies, but are currently represented as files as a migration step.
     */
    Set<LocalFileDependencyMetadata> getFiles();

    /**
     * Returns a copy of this variant, except with all artifacts transformed by the given transformer.
     *
     * @param artifactTransformer A transformer applied to all artifacts and sub-variant artifacts.
     *
     * @return A copy of this variant, with the given transformer applied to all artifacts.
     */
    LocalVariantGraphResolveState copyWithTransformedArtifacts(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer);

}
