/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;

import javax.annotation.Nullable;
import java.util.List;

public interface ResolvedComponentResultInternal extends ResolvedComponentResult {
    /**
     * Used by the Android plugin. Do not use this method.
     */
    @Deprecated
    String getRepositoryName();

    /**
     * <p>Returns the id of the repository used to source this component, or {@code null} if this component was not resolved from a repository.
     */
    @Nullable
    String getRepositoryId();

    /**
     * Returns all the variants of this component available for selection. Does not include variants that cannot be consumed, which means this
     * may not include all the variants returned by {@link #getVariants()}.
     *
     * <p>
     * Note: for performance reasons,
     * {@link org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal#setReturnAllVariants(boolean)}
     * must be set to {@code true} for this to actually return all variants in all cases.
     * </p>
     *
     * @return all variants for this component
     * @since 7.5
     */
    List<ResolvedVariantResult> getAvailableVariants();
}
