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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.function.Supplier;

/**
 * Default implementation of {@link MinimalResolutionResult}.
 */
public class DefaultMinimalResolutionResult implements MinimalResolutionResult {

    private final long rootVariantId;
    private final Supplier<ResolvedComponentResultInternal> rootSource;
    private final ImmutableAttributes requestedAttributes;

    public DefaultMinimalResolutionResult(
        long rootVariantId,
        Supplier<ResolvedComponentResultInternal> rootSource,
        ImmutableAttributes requestedAttributes
    ) {
        this.rootVariantId = rootVariantId;
        this.rootSource = rootSource;
        this.requestedAttributes = requestedAttributes;
    }

    @Override
    public long getRootVariantId() {
        return rootVariantId;
    }

    @Override
    public Supplier<ResolvedComponentResultInternal> getRootSource() {
        return rootSource;
    }

    @Override
    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }
}
