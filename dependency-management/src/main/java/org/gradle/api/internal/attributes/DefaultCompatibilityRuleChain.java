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
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.CompatibilityRuleChain;
import org.gradle.internal.action.DefaultConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.Comparator;
import java.util.List;

public class DefaultCompatibilityRuleChain<T> implements CompatibilityRuleChain<T>, CompatibilityRule<T> {
    private final List<Action<? super CompatibilityCheckDetails<T>>> rules = Lists.newArrayList();
    private final Instantiator instantiator;
    private final IsolatableFactory isolatableFactory;

    public DefaultCompatibilityRuleChain(Instantiator instantiator, IsolatableFactory isolatableFactory) {
        this.instantiator = instantiator;
        this.isolatableFactory = isolatableFactory;
    }

    @Override
    public void ordered(Comparator<? super T> comparator) {
        Action<? super CompatibilityCheckDetails<T>> rule = AttributeMatchingRules.orderedCompatibility(comparator, false);
        rules.add(rule);
    }

    @Override
    public void reverseOrdered(Comparator<? super T> comparator) {
        Action<? super CompatibilityCheckDetails<T>> rule = AttributeMatchingRules.orderedCompatibility(comparator, true);
        rules.add(rule);
    }

    @Override
    public void add(Class<? extends AttributeCompatibilityRule<T>> rule, Action<? super ActionConfiguration> configureAction) {
        rules.add(new InstantiatingAction<>(DefaultConfigurableRules.of(DefaultConfigurableRule.of(rule, configureAction, isolatableFactory)),
            instantiator, new ExceptionHandler<>(rule)));
    }

    @Override
    public void add(final Class<? extends AttributeCompatibilityRule<T>> rule) {
        rules.add(new InstantiatingAction<>(DefaultConfigurableRules.of(DefaultConfigurableRule.of(rule)),
            instantiator, new ExceptionHandler<>(rule)));
    }

    @Override
    public void execute(CompatibilityCheckResult<T> result) {
        for (Action<? super CompatibilityCheckDetails<T>> rule : rules) {
            rule.execute(result);
            if (result.hasResult()) {
                return;
            }
        }
    }

    @Override
    public boolean doesSomething() {
        return !rules.isEmpty();
    }

    private static class ExceptionHandler<T> implements InstantiatingAction.ExceptionHandler<CompatibilityCheckDetails<T>> {

        private final Class<? extends AttributeCompatibilityRule<T>> rule;

        private ExceptionHandler(Class<? extends AttributeCompatibilityRule<T>> rule) {
            this.rule = rule;
        }

        @Override
        public void handleException(CompatibilityCheckDetails<T> details, Throwable throwable) {
            throw new AttributeMatchException(String.format("Could not determine whether value %s is compatible with value %s using %s.", details.getProducerValue(), details.getConsumerValue(), ModelType.of(rule).getDisplayName()), throwable);
        }
    }
}
