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
package org.gradle.api;

import java.util.List;
import java.util.Map;

/**
 * An attribute matching strategy is responsible for providing information about how an {@link Attribute}
 * is matched during dependency resolution. In particular, it will tell if a value, provided by a consumer,
 * is compatible with a value provided by a candidate.
 * @param <T> the type of the attribute
 */
@Incubating
public interface AttributeMatchingStrategy<T> {
    /**
     * Tells if the candidate value is compatible with the requested value. Compatibility doesn't mean
     * equality. A candidate value may be different from the requested value, but still compatible.
     * For example, Java 6 classes are compatible with a Java 7 runtime.
     * @param requestedValue the requested value
     * @param candidateValue a candidate value
     * @return true if the candidate value is <i>compatible with</i> the requested value
     */
    boolean isCompatible(T requestedValue, T candidateValue);

    /**
     * Selects the best matches from a list of compatible ones. The list of compatible sets
     * is expressed as a {@link Map} which key is a candidate, and which value is the compatible value
     * of this candidate. It is implied that this method is only called with compatible values, so
     * the objective of this method is to discriminate (or order) compatible values, and return only
     * the best ones.
     *
     * The result of the selection process is going to depend on the result of this method: if it
     * returns a single value, then there's a clear winner. If it returns more than one value, then
     * it means that they are equivalent and that the strategy cannot discriminate between them.
     *
     * The result of this operation is never empty: since the map we pass only contains compatible
     * values, it is an error to say that there's no best match in that list. Similarly, it is
     * an error to return a value which is not contained in the key set of the candidates map.
     *
     * @param requestedValue the value to compare against
     * @param compatibleValues the map of candidate values
     * @param <K> the type of the candidate
     * @return a list of best matches. Must never be empty.
     */
    <K> List<K> selectClosestMatch(T requestedValue, Map<K, T> compatibleValues);

}
