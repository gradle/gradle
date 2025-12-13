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

import java.util.List;

/**
 * Something that can participate in multiple-candidate attribute matching. During multiple-candidate
 * matching, candidates are compared based on their attributes and the best match is selected. Compatibility
 * and disambiguation rules from attribute schemas are used to determine the best match.
 *
 * @see AttributeMatcher#matchMultipleCandidates(List, ImmutableAttributes)
 */
public interface AttributeMatchingCandidate {

    /**
     * Get the attributes that describe this candidate.
     */
    ImmutableAttributes getAttributes();

}
