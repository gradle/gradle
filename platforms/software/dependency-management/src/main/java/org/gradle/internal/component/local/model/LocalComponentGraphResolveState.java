/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;

/**
 * A specialized {@link ComponentGraphResolveState} for local components (ie project dependencies).
 *
 * <p>Instances of this type are cached and reused for multiple graph resolutions, possibly in parallel. This means that the implementation must be thread-safe.
 */
@ThreadSafe
public interface LocalComponentGraphResolveState extends ComponentGraphResolveState {

    /**
     * Get a variant derived from the configuration with the given name, or null if no such
     * configuration exists.
     *
     * This method should only be used to fetch the root variant of a resolution. There are plans
     * to migrate away from this method for that purpose.
     *
     * TODO: This is a legacy mechanism, and does not verify that the named configuration is
     * consumable. Prefer {@link GraphSelectionCandidates#getVariantByConfigurationName(String)}.
     *
     * <strong>Do not use this method, as it will be removed in Gradle 9.0.</strong>
     */
    @Nullable
    @Deprecated
    LocalVariantGraphResolveState getConfigurationLegacy(String configurationName);

    ModuleVersionIdentifier getModuleVersionId();

    @Override
    LocalComponentGraphResolveMetadata getMetadata();

    /**
     * Copies this state, but with the new component ID and the artifacts transformed by the given transformer.
     */
    LocalComponentGraphResolveState copy(ComponentIdentifier newComponentId, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer);

    /**
     * We currently allow a configuration that has been partially observed for resolution to be modified
     * in a beforeResolve callback.
     *
     * To reduce the number of instances of root component metadata we create, we mark all configurations
     * as dirty and in need of re-evaluation when we see certain types of modifications to a configuration.
     *
     * In the future, we could narrow the number of configurations that need to be re-evaluated, but it would
     * be better to get rid of the behavior that allows configurations to be modified once they've been observed.
     *
     * @see org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder.MetadataHolder#tryCached(ComponentIdentifier)
     */
    void reevaluate();

    @Override
    LocalComponentGraphSelectionCandidates getCandidatesForGraphVariantSelection();

    interface LocalComponentGraphSelectionCandidates extends GraphSelectionCandidates {

        /**
         * Get all variants that can be selected for this component. This includes both:
         * <ul>
         *     <li>Variant with attributes: those which can be selected through attribute matching</li>
         *     <li>Variant without attributes: those which can be selected by configuration name</li>
         * </ul>
         */
        List<LocalVariantGraphResolveState> getAllSelectableVariants();

    }
}
