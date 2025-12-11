/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.internal.component.model.VariantIdentifier;

/**
 * Internal counterpart to {@link ResolvedArtifactResult}
 */
public interface ResolvedArtifactResultInternal extends ResolvedArtifactResult {

    /**
     * Get the ID of the variant in the graph that produced this artifact.
     * <p>
     * This should eventually become public API.
     */
    VariantIdentifier getSourceVariantId();

}
