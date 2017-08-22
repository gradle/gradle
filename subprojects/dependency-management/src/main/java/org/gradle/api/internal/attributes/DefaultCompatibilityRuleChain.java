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
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.Comparator;
import java.util.List;

public class DefaultCompatibilityRuleChain<T> implements CompatibilityRuleChain<T>, CompatibilityRule<T> {
    private static final Object[] NO_PARAMS = new Object[0];
    private final List<Action<? super CompatibilityCheckDetails<T>>> rules = Lists.newArrayList();
    private final Instantiator instantiator;

    public DefaultCompatibilityRuleChain(Instantiator instantiator) {
        this.instantiator = instantiator;
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
        DefaultActionConfiguration configuration = new DefaultActionConfiguration();
        configureAction.execute(configuration);
        rules.add(new InstantiatingAction<T>(rule, configuration.getParams(), instantiator));
    }

    @Override
    public void add(final Class<? extends AttributeCompatibilityRule<T>> rule) {
        rules.add(new InstantiatingAction<T>(rule, NO_PARAMS, instantiator));
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

    private static class InstantiatingAction<T> implements Action<CompatibilityCheckDetails<T>> {
        private final Class<? extends AttributeCompatibilityRule<T>> rule;
        private final Object[] params;
        private final Instantiator instantiator;

        InstantiatingAction(Class<? extends AttributeCompatibilityRule<T>> rule, Object[] params, Instantiator instantiator) {
            this.rule = rule;
            this.params = params;
            this.instantiator = instantiator;
        }

        @Override
        public void execute(CompatibilityCheckDetails<T> details) {
            try {
                AttributeCompatibilityRule<T> instance = instantiator.newInstance(rule, params);
                instance.execute(details);
            } catch (Throwable t) {
                throw new AttributeMatchException(String.format("Could not determine whether value %s is compatible with value %s using %s.", details.getProducerValue(), details.getConsumerValue(), ModelType.of(rule).getDisplayName()), t);
            }
        }
    }
}
