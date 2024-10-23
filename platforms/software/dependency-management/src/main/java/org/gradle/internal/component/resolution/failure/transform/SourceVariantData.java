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

package org.gradle.internal.component.resolution.failure.transform;

import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * A lightweight replacement for {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant ResolvedVariant}
 * that contains the data in the root variant that is used to begin an artifact transformation chain.
 * <p>
 * Immutable data class.  Meant to be easily serialized as part of build operation recording and tracing.
 */
public final class SourceVariantData {
    private final String variantName;
    private final ImmutableAttributes attributes;

    public SourceVariantData(String variantName, ImmutableAttributes attributes) {
        this.variantName = variantName;
        this.attributes = attributes;
    }

    public String getVariantName() {
        return variantName;
    }

    public ImmutableAttributes getAttributes() {
        return attributes;
    }
}
