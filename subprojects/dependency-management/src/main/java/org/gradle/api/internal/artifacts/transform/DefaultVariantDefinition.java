/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;

/**
 * Default implementation of {@link VariantDefinition}.
 */
public class DefaultVariantDefinition implements VariantDefinition {
    private final DefaultVariantDefinition previous;
    private final ImmutableAttributes attributes;
    private final TransformationChain transformationChain;
    private final TransformationStep transformationStep;

    public DefaultVariantDefinition(@Nullable DefaultVariantDefinition previous, ImmutableAttributes attributes, TransformationStep transformationStep) {
        this.previous = previous;
        this.attributes = attributes;
        this.transformationChain = new TransformationChain(previous == null ? null : previous.getTransformationChain(), transformationStep);
        this.transformationStep = transformationStep;
    }

    @Override
    public ImmutableAttributes getTargetAttributes() {
        return attributes;
    }

    @Override
    public TransformationChain getTransformationChain() {
        return transformationChain;
    }

    @Override
    public TransformationStep getTransformationStep() {
        return transformationStep;
    }

    @Nullable
    @Override
    public VariantDefinition getPrevious() {
        return previous;
    }

    private int getDepth() {
        return previous == null ? 1 : previous.getDepth() + 1;
    }

    public String toString() {
        if (previous != null) {
            return previous + " <- (" + getDepth() + ") " + transformationStep;
        } else {
            return "(" + getDepth() + ") " + transformationStep;
        }
    }
}
