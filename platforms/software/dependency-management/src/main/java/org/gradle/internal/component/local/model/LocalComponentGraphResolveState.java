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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;

/**
 * A specialized {@link ComponentGraphResolveState} for local components (ie project dependencies).
 *
 * <p>Instances of this type are cached and reused for multiple graph resolutions, possibly in parallel. This means that the implementation must be thread-safe.
 */
@ThreadSafe
public interface LocalComponentGraphResolveState extends ComponentGraphResolveState {

    ModuleVersionIdentifier getModuleVersionId();

    @Override
    LocalComponentGraphResolveMetadata getMetadata();

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

        /**
         * Returns the variant that is identified by the given configuration name.
         */
        @Nullable
        VariantGraphResolveState getVariantByConfigurationName(String name);

    }

}
