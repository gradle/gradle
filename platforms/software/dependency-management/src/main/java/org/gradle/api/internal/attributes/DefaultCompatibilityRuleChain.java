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
import org.gradle.api.ActionConfiguration;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.CompatibilityRuleChain;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DefaultCompatibilityRuleChain<T> implements CompatibilityRuleChain<T> {

    private final List<Action<? super CompatibilityCheckDetails<T>>> rules = new ArrayList<>();
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
    public void add(Class<? extends AttributeCompatibilityRule<T>> ruleClass, Action<? super ActionConfiguration> configureAction) {
        ConfigurableRule<CompatibilityCheckDetails<T>> rule = DefaultConfigurableRule.of(ruleClass, configureAction, isolatableFactory);
        rules.add(createAction(rule, instantiator));
    }

    @Override
    public void add(final Class<? extends AttributeCompatibilityRule<T>> ruleClass) {
        ConfigurableRule<CompatibilityCheckDetails<T>> rule = DefaultConfigurableRule.of(ruleClass);
        rules.add(createAction(rule, instantiator));
    }

    public List<Action<? super CompatibilityCheckDetails<T>>> getRules() {
        return rules;
    }

    public static <T> Action<CompatibilityCheckDetails<T>> createAction(
        ConfigurableRule<CompatibilityCheckDetails<T>> rule,
        Instantiator instantiator
    ) {
        Class<?> ruleClass = rule.getRuleClass();
        Action<CompatibilityCheckDetails<T>> delegate = new InstantiatingAction<>(DefaultConfigurableRules.of(rule), instantiator, new ExceptionHandler<>(ruleClass));
        return new ValidatingAction<>(ruleClass, delegate);
    }

    /**
     * Wraps an {@link InstantiatingAction} with a check that the rule's declared type parameter is
     * a supported attribute value type. Implemented as a named inner class rather than a lambda so
     * it can be serialized by the configuration cache (lambda-synthesized classes aren't visible to
     * the CC classloader hierarchy).
     */
    private static class ValidatingAction<T> implements Action<CompatibilityCheckDetails<T>> {
        private final Class<?> ruleClass;
        private final Action<CompatibilityCheckDetails<T>> delegate;

        /**
         * Throttles the reflective type-parameter validation, which would otherwise run on every
         * attribute match. Kept as per-instance state (rather than a static across all rules) so it
         * neither leaks the rule's plugin classloader for the life of the daemon nor suppresses the
         * deprecation on builds after the first. Transient so a config-cache restore re-validates
         * rather than inheriting a stale "already validated" flag.
         */
        private transient volatile boolean validated;

        ValidatingAction(Class<?> ruleClass, Action<CompatibilityCheckDetails<T>> delegate) {
            this.ruleClass = ruleClass;
            this.delegate = delegate;
        }

        @Override
        public void execute(CompatibilityCheckDetails<T> details) {
            if (!validated) {
                validated = true;
                AttributeTypeValidator.validateRuleTypeParameter(ruleClass, AttributeCompatibilityRule.class);
            }
            delegate.execute(details);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return delegate.equals(((ValidatingAction<?>) o).delegate);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    private static class ExceptionHandler<T> implements InstantiatingAction.ExceptionHandler<CompatibilityCheckDetails<T>> {

        private final Class<?> rule;

        private ExceptionHandler(Class<?> rule) {
            this.rule = rule;
        }

        @Override
        public void handleException(CompatibilityCheckDetails<T> details, Throwable throwable) {
            throw new AttributeMatchException(String.format("Could not determine whether value %s is compatible with value %s using %s.", details.getProducerValue(), details.getConsumerValue(), ModelType.of(rule).getDisplayName()), throwable);
        }
    }

}
