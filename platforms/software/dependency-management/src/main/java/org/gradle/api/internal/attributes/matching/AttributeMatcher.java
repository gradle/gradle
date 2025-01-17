/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collection;
import java.util.List;

public interface AttributeMatcher {

    /**
     * Determines whether the given candidate is compatible with the requested criteria.
     */
    boolean isMatchingCandidate(ImmutableAttributes candidate, ImmutableAttributes requested);

    /**
     * Determines whether two candidates are mutually compatible.
     *
     * @return true if for each shared key in the provided attribute sets, the corresponding
     * attribute value in each set is compatible. false otherwise.
     */
    boolean areMutuallyCompatible(ImmutableAttributes first, ImmutableAttributes second);

    /**
     * Determine if a candidate value compatible with the requested criteria
     * for a some attribute.
     */
    <T> boolean isMatchingValue(Attribute<T> attribute, T candidate, T requested);

    /**
     * Selects all matches from {@code candidates} that are compatible with the {@code requested}
     * criteria attributes. Then, if there is more than one match, performs disambiguation to attempt
     * to reduce the set of matches to a more preferred subset.
     */
    <T extends HasAttributes> List<T> matchMultipleCandidates(
        Collection<? extends T> candidates,
        ImmutableAttributes requested
    );

    // TODO: Merge this with ResolutionCandidateAssessor
    List<MatchingDescription<?>> describeMatching(ImmutableAttributes candidate, ImmutableAttributes requested);

    class MatchingDescription<T> {
        private final Attribute<T> requestedAttribute;
        private final AttributeValue<T> requestedValue;
        private final AttributeValue<T> found;
        private final boolean match;

        public MatchingDescription(Attribute<T> requestedAttribute, AttributeValue<T> requestedValue, AttributeValue<T> found, boolean match) {
            this.requestedAttribute = requestedAttribute;
            this.requestedValue = requestedValue;
            this.found = found;
            this.match = match;
        }

        public Attribute<T> getRequestedAttribute() {
            return requestedAttribute;
        }

        public AttributeValue<T> getRequestedValue() {
            return requestedValue;
        }

        public AttributeValue<T> getFound() {
            return found;
        }

        public boolean isMatch() {
            return match;
        }
    }
}
