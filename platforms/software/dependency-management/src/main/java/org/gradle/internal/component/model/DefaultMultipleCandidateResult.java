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

package org.gradle.internal.component.model;

import com.google.common.collect.Sets;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public class DefaultMultipleCandidateResult<T> implements MultipleCandidatesResult<T> {
    private final Set<T> candidateValues;
    private final T consumerValue;

    // Match recording is optimized for the general case of a single match
    private T singleMatch;
    private Set<T> multipleMatches;

    public DefaultMultipleCandidateResult(@Nullable T consumerValue, Set<T> candidateValues) {
        if (candidateValues.isEmpty() || (consumerValue != null && candidateValues.size() == 1)) {
            throw new IllegalArgumentException("Insufficient number of candidate values: " + candidateValues.size());
        }
        for (T candidateValue : candidateValues) {
            if (candidateValue == null)  {
                throw new IllegalArgumentException("candidateValues cannot contain null elements");
            }
        }

        this.candidateValues = candidateValues;
        this.consumerValue = consumerValue;
    }

    @Override
    public boolean hasResult() {
        return singleMatch != null || multipleMatches!=null;
    }

    @Override
    public Set<T> getMatches() {
        assert hasResult();
        if (singleMatch != null) {
            return Collections.singleton(singleMatch);
        }
        return multipleMatches;
    }

    @Nullable
    @Override
    public T getConsumerValue() {
        return consumerValue;
    }

    @Override
    public Set<T> getCandidateValues() {
        return candidateValues;
    }

    @Override
    public void closestMatch(T candidate) {
        if (singleMatch == null) {
            if (multipleMatches == null) {
                singleMatch = candidate;
            } else {
                multipleMatches.add(candidate);
            }
            return;
        }
        if (singleMatch.equals(candidate)) {
            return;
        }
        multipleMatches = Sets.newHashSetWithExpectedSize(candidateValues.size());
        multipleMatches.add(singleMatch);
        multipleMatches.add(candidate);
        singleMatch = null;
    }
}
