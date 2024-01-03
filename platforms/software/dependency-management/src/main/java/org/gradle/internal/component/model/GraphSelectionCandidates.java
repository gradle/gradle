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

import javax.annotation.Nullable;
import java.util.List;

public interface GraphSelectionCandidates {
    /**
     * Should variant selection be used?
     * @return true when variant selection should be used, false when the legacy variant should be used.
     */
    boolean isUseVariants();

    /**
     * Returns the set of variants to select from. Should only be called when {@link #isUseVariants()} returns true.
     */
    List<? extends VariantGraphResolveState> getVariants();

    /**
     * Returns the configuration to use when variant selection is not being used.
     */
    @Nullable
    ConfigurationGraphResolveState getLegacyConfiguration();

    /**
     * The set of consumable configurations available. Used for diagnostics.
     */
    List<? extends ConfigurationGraphResolveMetadata> getCandidateConfigurations();
}
