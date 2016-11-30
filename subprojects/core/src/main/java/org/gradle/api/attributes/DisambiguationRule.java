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
 * <p>A disambiguation rule can select the best match from a list of {@link AttributeValue candidate}.</p>
 *
 * <p>A rule <i>can</i> express an preference by calling the @{link {@link MultipleCandidatesDetails#closestMatch(HasAttributes)}
 * method to tell that a candidate is the best one.</p>
 *
 * <p>It is not mandatory for a rule to choose, and it is not an error to select multiple candidates.</p>
 *
 * @param <T> the type of the attribute
 */
@Incubating
public interface DisambiguationRule<T> {
    /**
     * Allows selecting best matches for a given attribute. This method is passed a {@link MultipleCandidatesDetails details}
     * object which gives access to the consumer value as well as the candidate producer values. Both the consumer and
     * producer values can be present, missing or unknown.
     *
     */
    void selectClosestMatch(MultipleCandidatesDetails<T> details);
}
