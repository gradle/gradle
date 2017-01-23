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

import java.util.Set;

/**
 * Provides context about candidates for an attribute. In particular, this class gives access to
 * the list of candidates on the producer side.
 *
 * @param <T> the concrete type of the attribute
 *
 * @since 3.3
 */
@Incubating
public interface MultipleCandidatesDetails<T> {

    /**
     * A set of candidate values.
     * @return the set of candidates
     */
    Set<T> getCandidateValues();

    /**
     * Calling this method indicates that the candidate is the closest match. It is allowed to call this method several times with
     * different values, in which case it indicates that multiple candidates are equally compatible.
     *
     * @param candidate the closest match
     */
    void closestMatch(T candidate);

}
