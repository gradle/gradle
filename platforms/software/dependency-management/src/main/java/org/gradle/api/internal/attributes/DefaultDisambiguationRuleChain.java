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

import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.DisambiguationRuleChain;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.internal.action.DefaultConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class DefaultDisambiguationRuleChain<T> implements DisambiguationRuleChain<T> {
    private final List<Action<? super MultipleCandidatesDetails<T>>> rules = new ArrayList<>();
    private final Instantiator instantiator;
    private final IsolatableFactory isolatableFactory;

    public DefaultDisambiguationRuleChain(Instantiator instantiator, IsolatableFactory isolatableFactory) {
        this.instantiator = instantiator;
        this.isolatableFactory = isolatableFactory;
    }

    @Override
    public void add(final Class<? extends AttributeDisambiguationRule<T>> rule, Action<? super ActionConfiguration> configureAction) {
        this.rules.add(new InstantiatingAction<>(DefaultConfigurableRules.of(DefaultConfigurableRule.of(rule, configureAction, isolatableFactory)),
            instantiator, new ExceptionHandler<>(rule)));
    }

    @Override
    public void add(final Class<? extends AttributeDisambiguationRule<T>> rule) {
        this.rules.add(new InstantiatingAction<>(DefaultConfigurableRules.of(DefaultConfigurableRule.of(rule)),
            instantiator, new ExceptionHandler<>(rule)));
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

    public List<Action<? super MultipleCandidatesDetails<T>>> getRules() {
        return rules;
    }

    public static class ExceptionHandler<T> implements InstantiatingAction.ExceptionHandler<MultipleCandidatesDetails<T>> {

        private final Class<?> rule;

        public ExceptionHandler(Class<?> rule) {

            this.rule = rule;
        }

        @Override
        public void handleException(MultipleCandidatesDetails<T> details, Throwable throwable) {
            Set<T> orderedValues = Sets.newTreeSet(Ordering.usingToString());
            orderedValues.addAll(details.getCandidateValues());
            throw new AttributeMatchException(String.format("Could not select value from candidates %s using %s.", orderedValues, ModelType.of(rule).getDisplayName()), throwable);
        }

    }
}
