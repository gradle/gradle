/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.internal.component.model.VariantIdentifier;

/**
 * Internal counterpart of {@link ResolvedVariantResult}.
 */
public interface ResolvedVariantResultInternal extends ResolvedVariantResult {

    /**
     * The identifier of this variant, unique among all variants in a dependency graph.
     */
    VariantIdentifier getId();

    @Override
    default ComponentIdentifier getOwner() {
        return getId().getComponentId();
    }

}
