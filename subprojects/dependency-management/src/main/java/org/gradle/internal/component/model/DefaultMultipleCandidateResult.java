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

import org.gradle.api.internal.attributes.MultipleCandidatesResult;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class DefaultMultipleCandidateResult<T> implements MultipleCandidatesResult<T> {
    private final Set<T> candidateValues;
    private final T consumerValue;
    private Set<T> matches;

    public DefaultMultipleCandidateResult(@Nullable T consumerValue, Set<T> candidateValues) {
        assert candidateValues.size() > 1;
        this.candidateValues = candidateValues;
        this.consumerValue = consumerValue;
    }

    @Override
    public boolean hasResult() {
        return matches != null;
    }

    public Set<T> getMatches() {
        assert matches != null;
        return matches;
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
        if (matches == null) {
            matches = new HashSet<T>(4);
        }
        matches.add(candidate);
    }
}
