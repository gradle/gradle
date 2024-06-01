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

import org.gradle.api.artifacts.Dependency;

import javax.annotation.Nullable;
import java.util.List;

public interface GraphSelectionCandidates {

    /**
     * Returns the set of variants to select from during attribute matching, or an empty list of this
     * component does not support attribute matching.
     */
    List<? extends VariantGraphResolveState> getVariantsForAttributeMatching();

    /**
     * Returns the variant to use when attribute-based variant selection is not enabled.
     */
    @Nullable
    default VariantGraphResolveState getLegacyVariant() {
        return getVariantByConfigurationName(Dependency.DEFAULT_CONFIGURATION);
    }

    /**
     * Returns the variant that is identified by the given configuration name.
     */
    @Nullable
    VariantGraphResolveState getVariantByConfigurationName(String name);
}
