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
package org.gradle.api.internal.attributes;

import org.gradle.api.Action;
import org.gradle.api.attributes.MultipleCandidatesDetails;

import java.util.Comparator;
import java.util.Set;

public class DefaultOrderedDisambiguationRule<T> implements Action<MultipleCandidatesDetails<T>> {
    private final Comparator<? super T> comparator;
    private final boolean pickFirst;

    public DefaultOrderedDisambiguationRule(Comparator<? super T> comparator, boolean pickFirst) {
        this.comparator = comparator;
        this.pickFirst = pickFirst;
    }

    @Override
    public void execute(MultipleCandidatesDetails<T> details) {
        Set<T> candidateValues = details.getCandidateValues();
        T min = null;
        T max = null;
        for (T value : candidateValues) {
            if (min == null || comparator.compare(value, min) < 0) {
                min = value;
            }
            if (max == null || comparator.compare(value, max) > 0) {
                max = value;
            }
        }
        T cmp = pickFirst ? min : max;
        if (cmp != null) {
            for (T value : candidateValues) {
                if (value.equals(cmp)) {
                    details.closestMatch(value);
                }
            }
        }
    }
}
