/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;

/**
 * An attribute matching strategy is responsible for providing information about how an {@link Attribute}
 * is matched during dependency resolution. In particular, it will tell if a value, provided by a consumer,
 * is compatible with a value provided by a candidate.
 * @param <T> the type of the attribute
 *
 * @since 3.3
 */
@Incubating
public interface AttributeMatchingStrategy<T> {
    /**
     * Called whenever we need to check if a value from a consumer is compatible with the value from a
     * producer. Both the consumer and producer values can be present, missing or unknown.
     * @param details the check details
     */
    void checkCompatibility(CompatibilityCheckDetails<T> details);

    /**
     * Allows selecting best matches for a given attribute. This method is passed a {@link MultipleCandidatesDetails details}
     * object which gives access to the consumer value as well as the candidate producer values. Both the consumer and
     * producer values can be present, missing or unknown.
     *
     * @param <K> the type of the candidate
     */
    <K> void selectClosestMatch(MultipleCandidatesDetails<T, K> details);

}
