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

import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * Represents a variant which is produced as the result of applying an artifact transformation chain
 * to a root producer variant.
 */
public class TransformedVariant implements HasAttributes {
    private final ResolvedVariant root;
    private final VariantDefinition chain;

    public TransformedVariant(ResolvedVariant root, VariantDefinition chain) {
        this.root = root;
        this.chain = chain;
    }

    /**
     * @return The chain of variants which result from applying the transformation chain to the root variant.
     */
    public VariantDefinition getVariantChain() {
        return chain;
    }

    /**
     * @return The transformation chain to apply to the root producer variant.
     */
    public TransformationChain getTransformationChain() {
        return chain.getTransformationChain();
    }

    /**
     * @return The root producer variant which the transform chain is applied to.
     */
    public ResolvedVariant getRoot() {
        return root;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return chain.getTargetAttributes();
    }

    @Override
    public String toString() {
        return root.asDescribable().getDisplayName() + " <- " + chain + " = " + getAttributes();
    }
}
