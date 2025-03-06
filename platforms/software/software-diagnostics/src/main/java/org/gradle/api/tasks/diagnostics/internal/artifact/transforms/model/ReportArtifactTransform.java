/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.artifact.transforms.model;

import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * Lightweight, immutable model of an Artifact Transform for reporting.
 */
public final class ReportArtifactTransform {
    private final String displayName;
    private final Class<?> transformClass;
    private final ImmutableAttributes fromAttributes;
    private final ImmutableAttributes toAttributes;

    private final boolean cacheable;

    public ReportArtifactTransform(String displayName, Class<?> transformClass, ImmutableAttributes fromAttributes, ImmutableAttributes toAttributes, boolean cacheable) {
        this.displayName = displayName;
        this.transformClass = transformClass;
        this.fromAttributes = fromAttributes;
        this.toAttributes = toAttributes;
        this.cacheable = cacheable;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Class<?> getTransformClass() {
        return transformClass;
    }

    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    public ImmutableAttributes getToAttributes() {
        return toAttributes;
    }

    public boolean isCacheable() {
        return cacheable;
    }
}
