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
import org.gradle.api.attributes.AttributeValue;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.CompatibilityRule;
import org.gradle.api.attributes.OrderedCompatibilityRule;

import java.util.List;

public class DefaultCompatibilityRuleChain<T> implements CompatibilityRuleChainInternal<T> {

    private final List<CompatibilityRule<T>> rules = Lists.newArrayList();

    private boolean failEventually = true;

    @Override
    public void addEqualityCheck() {
        add(AttributeMatchingRules.<T>equalityCompatibility());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends Comparable<U>> OrderedCompatibilityRule<U> addOrderedCheck() {
        OrderedCompatibilityRule<U> rule = AttributeMatchingRules.orderedCompatibility();
        add((CompatibilityRule<T>) rule);
        return rule;
    }

    @Override
    public void add(CompatibilityRule<T> rule) {
        rules.add(rule);
    }

    @Override
    public void setRules(List<CompatibilityRule<T>> rules) {
        this.rules.clear();
        this.rules.addAll(rules);
    }

    @Override
    public void eventuallyIncompatible() {
        failEventually = true;
    }

    @Override
    public void eventuallyCompatible() {
        failEventually = false;
    }

    @Override
    public void checkCompatibility(CompatibilityCheckDetails<T> details) {
        State<T> state = new State<T>(details);
        for (CompatibilityRule<T> rule : rules) {
            rule.checkCompatibility(state);
            if (state.determined) {
                return;
            }
        }
        if (!state.determined) {
            if (failEventually) {
                details.incompatible();
            } else {
                details.compatible();
            }
        }
    }

    private static class State<T> implements CompatibilityCheckDetails<T> {
        private final CompatibilityCheckDetails<T> delegate;
        private boolean determined;

        private State(CompatibilityCheckDetails<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public AttributeValue<T> getConsumerValue() {
            return delegate.getConsumerValue();
        }

        @Override
        public AttributeValue<T> getProducerValue() {
            return delegate.getProducerValue();
        }

        @Override
        public void compatible() {
            determined = true;
            delegate.compatible();
        }

        @Override
        public void incompatible() {
            determined = true;
            delegate.incompatible();
        }
    }
}
