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

package org.gradle.api.tasks.diagnostics.internal.artifacttransforms.model;

import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Lightweight, immutable model of an Artifact Transform for reporting.
 */
public final class ReportArtifactTransform {
    @Nullable
    private final String name;

    private final Class<?> transformClass;
    private final ImmutableAttributes fromAttributes;
    private final ImmutableAttributes toAttributes;

    private final boolean cacheable;

    public ReportArtifactTransform(@Nullable String name, Class<?> transformClass, ImmutableAttributes fromAttributes, ImmutableAttributes toAttributes, boolean cacheable) {
        this.name = name;
        this.transformClass = transformClass;
        this.fromAttributes = fromAttributes;
        this.toAttributes = toAttributes;
        this.cacheable = cacheable;
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

    public boolean isNamed() {
        return name != null;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }
}
