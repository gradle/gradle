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
import org.gradle.api.ActionConfiguration;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.DisambiguationRuleChain;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.Comparator;
import java.util.List;

public class DefaultDisambiguationRuleChain<T> implements DisambiguationRuleChain<T>, DisambiguationRule<T> {
    private static final Object[] NO_PARAMS = new Object[0];
    private final List<Action<? super MultipleCandidatesDetails<T>>> rules = Lists.newArrayList();
    private final Instantiator instantiator;

    public DefaultDisambiguationRuleChain(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public void add(Class<? extends AttributeDisambiguationRule<T>> rule, Action<? super ActionConfiguration> configureAction) {
        DefaultActionConfiguration configuration = new DefaultActionConfiguration();
        configureAction.execute(configuration);
        this.rules.add(new InstantiatingAction<T>(rule, configuration.getParams(), instantiator));
    }

    @Override
    public void add(final Class<? extends AttributeDisambiguationRule<T>> rule) {
        this.rules.add(new InstantiatingAction<T>(rule, NO_PARAMS, instantiator));
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
    public void execute(MultipleCandidatesResult<T> details) {
        for (Action<? super MultipleCandidatesDetails<T>> rule : rules) {
            rule.execute(details);
            if (details.hasResult()) {
                return;
            }
        }
    }

    private static class InstantiatingAction<T> implements Action<MultipleCandidatesDetails<T>> {
        private final Class<? extends AttributeDisambiguationRule<T>> rule;
        private final Object[] params;
        private final Instantiator instantiator;

        InstantiatingAction(Class<? extends AttributeDisambiguationRule<T>> rule, Object[] params, Instantiator instantiator) {
            this.rule = rule;
            this.params = params;
            this.instantiator = instantiator;
        }

        @Override
        public void execute(MultipleCandidatesDetails<T> details) {
            try {
                AttributeDisambiguationRule<T> instance = instantiator.newInstance(rule, params);
                instance.execute(details);
            } catch (Throwable t) {
                throw new AttributeMatchException(String.format("Could not select value from candidates %s using %s.", details.getCandidateValues(), ModelType.of(rule).getDisplayName()), t);
            }
        }
    }
}
