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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * A representation of the registered artifact transforms for a project that omits the details of the
 * transforms themselves, other than the information required for selecting artifact transforms.
 * <p>
 * This class serves as an immutable, project-independent representation of the selectable transforms,
 * which allows artifact transform selection results to be cached so that projects with equivalent registered
 * transforms do not need to perform transform selection multiple times.
 */
public class CacheableTransforms {

    private final List<CacheableTransform> transforms;

    private final int hashCode;

    public CacheableTransforms(List<CacheableTransform> transforms) {
        this.transforms = transforms;
        this.hashCode = transforms.hashCode();
    }

    /**
     * Create a {@link CacheableTransforms} that contains only the project-independent details required
     * to perform artifact transform selection.
     */
    public static CacheableTransforms from(ImmutableList<TransformRegistration> registrations) {
        List<CacheableTransform> cachedTransforms = new ArrayList<>(registrations.size());
        for (int i = 0; i < registrations.size(); i++) {
            TransformRegistration registration = registrations.get(i);
            cachedTransforms.add(new CacheableTransform(
                i,
                registration.getFrom(),
                registration.getTo()
            ));
        }

        return new CacheableTransforms(cachedTransforms);
    }

    /**
     * Get all cacheable transforms in this instance.
     */
    public List<CacheableTransform> getTransforms() {
        return transforms;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CacheableTransforms that = (CacheableTransforms) o;
        return transforms.equals(that.transforms);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Represents a transform step in the transform chain detection algorithm. This class only refers to
     * the index of the source transform in the original registered transform list in order to be cacheable
     * while also representing a project-specific transform registration instance.
     */
    public static class CacheableTransform {

        private final int registrationIndex;
        private final ImmutableAttributes from;
        private final ImmutableAttributes to;

        private final int hashCode;

        public CacheableTransform(int registrationIndex, ImmutableAttributes from, ImmutableAttributes to) {
            this.registrationIndex = registrationIndex;
            this.from = from;
            this.to = to;

            this.hashCode = computeHashCode(from, registrationIndex, to);
        }

        private static int computeHashCode(ImmutableAttributes from, int registrationIndex, ImmutableAttributes to) {
            int result = registrationIndex;
            result = 31 * result + from.hashCode();
            result = 31 * result + to.hashCode();
            return result;
        }

        /**
         * The index in the original list of registered transforms that this cacheable transform corresponds to.
         */
        public int getRegistrationIndex() {
            return registrationIndex;
        }

        /**
         * The {@code from} attributes of the original transform registration.
         */
        public ImmutableAttributes getFrom() {
            return from;
        }

        /**
         * The {@code to} attributes of the original transform registration.
         */
        public ImmutableAttributes getTo() {
            return to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheableTransform that = (CacheableTransform) o;
            return registrationIndex == that.registrationIndex &&
                from == that.from && // ImmutableAttributes instances are interned.
                to == that.to;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

    }

}
