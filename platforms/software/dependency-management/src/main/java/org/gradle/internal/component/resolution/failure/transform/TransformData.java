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

import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * A lightweight replacement for {@link org.gradle.api.internal.artifacts.transform.TransformStep TransformStep}
 * that contains the data in each ArtifactTransform step that comprises an artifact transformation chain.
 * <p>
 * Immutable data class.  Meant to be easily serialized as part of build operation recording and tracing.
 */
public final class TransformData {
    private final Class<? extends TransformAction<?>> transformActionClass;
    private final String transformName;
    private final ImmutableAttributes fromAttributes;
    private final ImmutableAttributes toAttributes;

    public TransformData(Class<? extends TransformAction<?>> transformActionClass, String transformName, ImmutableAttributes fromAttributes, ImmutableAttributes toAttributes) {
        this.transformActionClass = transformActionClass;
        this.transformName = transformName;
        this.fromAttributes = fromAttributes;
        this.toAttributes = toAttributes;
    }

    public Class<? extends TransformAction<?>> getTransformActionClass() {
        return transformActionClass;
    }

    public String getTransformName() {
        return transformName;
    }

    /**
     * The set of attributes that will be modified by this Artifact Transform, with their original values.
     *
     * @return attributes as described
     */
    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    /**
     * The set of attributes that will be modified by this Artifact Transform, with their resulting values.
     *
     * @return attributes as described
     */
    public ImmutableAttributes getToAttributes() {
        return toAttributes;
    }
}
