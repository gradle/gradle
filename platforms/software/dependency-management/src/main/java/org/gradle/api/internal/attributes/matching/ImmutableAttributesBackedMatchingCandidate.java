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

package org.gradle.api.internal.attributes.matching;

import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * Wraps a standalone {@link ImmutableAttributes} so that it can participate in
 * attribute matching.
 */
public class ImmutableAttributesBackedMatchingCandidate implements AttributeMatchingCandidate {

    private final ImmutableAttributes attributes;

    public ImmutableAttributesBackedMatchingCandidate(ImmutableAttributes attributes) {
        this.attributes = attributes;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableAttributesBackedMatchingCandidate)) {
            return false;
        }

        ImmutableAttributesBackedMatchingCandidate that = (ImmutableAttributesBackedMatchingCandidate) o;
        return attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return attributes.hashCode();
    }

}
