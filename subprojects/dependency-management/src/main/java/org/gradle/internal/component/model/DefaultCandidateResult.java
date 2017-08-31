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

import com.google.common.collect.Multimap;
import org.gradle.api.internal.attributes.MultipleCandidatesResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class DefaultCandidateResult<T> implements MultipleCandidatesResult<Object> {
    private final Multimap<Object, T> candidatesByValue;
    private final Object consumerValue;
    private final List<T> best;
    private boolean done;

    public DefaultCandidateResult(Multimap<Object, T> candidatesByValue, @Nullable Object consumerValue, List<T> best) {
        this.candidatesByValue = candidatesByValue;
        this.consumerValue = consumerValue;
        this.best = best;
    }

    @Override
    public boolean hasResult() {
        return done;
    }

    @Nullable
    @Override
    public Object getConsumerValue() {
        return consumerValue;
    }

    @Override
    public Set<Object> getCandidateValues() {
        return candidatesByValue.keySet();
    }

    @Override
    public void closestMatch(Object candidate) {
        done = true;
        best.addAll(candidatesByValue.get(candidate));
    }
}
