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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class DefaultDisambiguationRuleChain<T> implements DisambiguationRuleChainInternal<T> {

    private final List<Action<? super MultipleCandidatesDetails<T>>> rules = Lists.newArrayList();

    @Override
    public void add(final Class<? extends AttributeDisambiguationRule<T>> rule) {
        this.rules.add(new Action<MultipleCandidatesDetails<T>>() {
            @Override
            public void execute(MultipleCandidatesDetails<T> details) {
                try {
                    AttributeDisambiguationRule<T> instance = DirectInstantiator.INSTANCE.newInstance(rule);
                    instance.execute(details);
                } catch (Throwable t) {
                    throw new AttributeMatchException(String.format("Could not select value from candidates %s using %s.", details.getCandidateValues(), ModelType.of(rule).getDisplayName()), t);
                }
            }
        });
    }

    @Override
    public void pickFirst(Comparator<? super T> comparator) {
        Action<? super MultipleCandidatesDetails<T>> rule = AttributeMatchingRules.orderedDisambiguation(comparator, true);
        rules.add(rule);
    }

    @Override
    public void pickLast(Comparator<? super T> comparator) {
        Action<? super MultipleCandidatesDetails<T>> rule = AttributeMatchingRules.orderedDisambiguation(comparator, false);
        rules.add(rule);
    }

    @Override
    public void execute(MultipleCandidatesDetails<T> details) {
        State<T> state = new State<T>(details);
        for (Action<? super MultipleCandidatesDetails<T>> rule : rules) {
            rule.execute(state);
            if (state.determined) {
                return;
            }
        }
        if (!state.determined) {
            SelectAllCompatibleRule.apply(details);
        }
    }

    private static class State<T> implements MultipleCandidatesDetails<T> {
        private final MultipleCandidatesDetails<T> delegate;
        private boolean determined;

        private State(MultipleCandidatesDetails<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<T> getCandidateValues() {
            return delegate.getCandidateValues();
        }

        @Override
        public void closestMatch(T candidate) {
            determined = true;
            delegate.closestMatch(candidate);
        }

    }
}
