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
 * A disambiguation rule for attributes which compares them using a {@link java.util.Comparator}.
 *
 * @param <T> the type of the attribute.
 */
@Incubating
public interface OrderedDisambiguationRule<T> extends DisambiguationRule<T> {
    /**
     * Disambiguate by selecting the first candidate in order. If the attribute value
     * is missing or unknown, then it is excluded from the list of candidate.
     *
     * @return this rule
     */
    OrderedDisambiguationRule<T> pickFirst();

    /**
     * Disambiguate by selecting the last candidate in order. If the attribute value
     * is missing or unknown, then it is excluded from the list of candidate.
     * @return this rule
     */
    OrderedDisambiguationRule<T> pickLast();
}
