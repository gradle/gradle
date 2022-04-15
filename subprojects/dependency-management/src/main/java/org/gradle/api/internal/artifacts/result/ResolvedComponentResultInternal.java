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
     * <p>Returns the name of the repository used to source this component, or {@code null} if this component was not resolved from a repository.
     */
    @Nullable
    String getRepositoryName();

    /**
     * Returns all the variants for this component, even ones that weren't selected.
     *
     * <p>
     * Note: for performance reasons,
     * {@link org.gradle.api.internal.artifacts.configurations.ConfigurationInternal#setReturnAllVariants(boolean)}
     * must be set to {@code true} for this to actually return all variants in all cases.
     * </p>
     *
     * @return all variants for this component
     * @since 7.5
     */
    List<ResolvedVariantResult> getAllVariants();

}
