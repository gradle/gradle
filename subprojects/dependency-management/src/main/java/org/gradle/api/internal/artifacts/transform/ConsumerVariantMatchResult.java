/*
 * Copyright 2020 the original author or authors.
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
import java.util.Collection;

public interface ConsumerVariantMatchResult {

    boolean hasMatches();

    Collection<ConsumerVariant> getMatches();

    class ConsumerVariant implements VariantDefinition {
        final ImmutableAttributes attributes;
        final Transformation transformation;
        final TransformationStep transformationStep;
        @Nullable
        final ConsumerVariant previous;
        final int depth;

        public ConsumerVariant(ImmutableAttributes attributes, TransformationStep transformationStep, @Nullable ConsumerVariant previous, int depth) {
            this.attributes = attributes;
            if (previous == null) {
                this.transformation = transformationStep;
            } else {
                this.transformation = new TransformationChain(previous.transformation, transformationStep);
            }
            this.transformationStep = transformationStep;
            this.previous = previous;
            this.depth = depth;
        }

        @Override
        public String toString() {
            if (previous != null) {
                return previous + " <- (" + depth + ") " + transformationStep;
            } else {
                return "(" + depth + ") " + transformationStep;
            }
        }

        @Override
        public ImmutableAttributes getTargetAttributes() {
            return attributes;
        }

        @Override
        public Transformation getTransformation() {
            return transformation;
        }

        @Override
        public TransformationStep getTransformationStep() {
            return transformationStep;
        }

        @Nullable
        @Override
        public VariantDefinition getSourceVariant() {
            return previous;
        }
    }
}
