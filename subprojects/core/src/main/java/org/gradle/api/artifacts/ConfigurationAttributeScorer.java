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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

/**
 * A configuration attribute scorer is similar to a {@link java.util.Comparator} but with a different contract
 * regarding the return value. A scorer will not result in a total order if used as a comparator. Instead, it
 * is used to discriminate between 3 cases (perfect match, match at some distance and no match).
 */
@Incubating
public interface ConfigurationAttributeScorer {
    /**
     * Compares a requested attribute value to the candidate attribute value. There are 3 possible outcomes:
     * <ul>
     *     <li>if attribute values match strictly, then this method must return 0.</li>
     *     <li>if attribute values don't match, then this method must return -1.</li>
     *     <li>if attributes do not strictly match, but a compatibility is found, then return a score greater than 0. The closer to 0, the closer to a match we are.</li>
     * </ul>
     * @param requestedValue the value from the source configuration
     * @param attributeValue the value from the target configuration, or the default value if not found
     * @return a score for the match
     */
    int score(String requestedValue, String attributeValue);
}
