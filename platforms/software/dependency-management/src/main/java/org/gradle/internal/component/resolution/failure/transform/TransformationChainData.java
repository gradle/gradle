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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a variant which is produced as the result of applying an artifact transform chain
 * to a root producer variant.
 * <p>
 * Immutable data class.  Meant to be easily serialized as part of build operation recording and tracing.
 */
public final class TransformationChainData {
    private final SourceVariantData startingVariant;
    private final ImmutableList<TransformData> steps;
    private final ImmutableAttributes finalAttributes;

    public TransformationChainData(SourceVariantData startingVariant, ImmutableList<TransformData> steps, ImmutableAttributes finalAttributes) {
        this.startingVariant = startingVariant;
        this.steps = steps;
        this.finalAttributes = finalAttributes;
    }

    /**
     * The variant that was used as the starting point for this chain of transformations.
     *
     * @return initial variant
     */
    public SourceVariantData getInitialVariant() {
        return startingVariant;
    }

    public String summarizeTransformations() {
        return steps.stream()
            .map(t -> "'" + t.getTransformName() + "'")
            .collect(Collectors.joining(" -> "));
    }

    public ImmutableList<TransformData> getSteps() {
        return steps;
    }

    /**
     * The complete resulting set of attributes on the "virtual variant" created by processing the source variant
     * completely through this transformation chain.
     * <p>
     * This explicitly includes attributes of the source variant that were not modified by any transformations.
     *
     * @return attributes as described
     */
    public ImmutableAttributes getFinalAttributes() {
        return finalAttributes;
    }

    /**
     * Obtain an object that represents this chain's distinct set of transformations such that it is equal to
     * any other chain containing the same set (<strong>not sequence</strong> - the
     * transforms can be in any order) of transforms from the same source variant.
     * <p>
     * Immutable data class.
     */
    public TransformationChainFingerprint fingerprint() {
        return new TransformationChainFingerprint(this);
    }

    /**
     * Immutable data class representing a unique set (<strong>not sequence</strong> - the
     * transforms can be in any order) of transforms from a given source variant in a transformation chain.
     * <p>
     * This type must properly implement {@link #equals(Object)} and {@link #hashCode()}.
     */
    public static final class TransformationChainFingerprint {
        private final SourceVariantData startingVariant;
        private final HashSet<TransformData> steps;

        public TransformationChainFingerprint(TransformationChainData chain) {
            startingVariant = chain.startingVariant;
            steps = new HashSet<>(chain.steps);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TransformationChainFingerprint that = (TransformationChainFingerprint) o;
            return Objects.equals(startingVariant, that.startingVariant) && steps.equals(that.steps);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(startingVariant);
            result = 31 * result + steps.hashCode();
            return result;
        }
    }
}
