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
package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import java.util.List;

/**
 * <p>A chain of compatibility checks, implemented as {@link CompatibilityRule rules}. By default
 * the chain is empty and will eventually tell the values are incompatible if no rule expressed
 * an opinion.</p>
 *
 * <p>For a given set of rules, the execution is done <i>in order</i>, and interrupts as soon as a rule
 * expressed an option (through {@link CompatibilityCheckDetails#compatible()} or {@link CompatibilityCheckDetails#incompatible()}).
 * </p>
 *
 * <p>If the end of the rule chain is reached and that no rule expressed an opinion then the decision whether to make attributes
 * compatible or not is taken depending on the calls to {@link #eventuallyCompatible()} or {@link #eventuallyIncompatible()} (the default)</p>
 *
 * @param <T> the type of the attribute
 */
@Incubating
@HasInternalProtocol
public interface CompatibilityRuleChain<T> {
    /**
     * Adds an equality check rule to this chain. An equality check rule will always express an opinion:
     * if the attributes are <i>equal</i> then the attributes are deemed compatible, otherwise they are
     * incompatible.
     */
    void addEqualityCheck();

    /**
     * Adds an ordered check rule to this chain. It is expected to call this method if and only if the
     * type of the attribute is {@link Comparable}. In that case compatibility is working as described
     * in {@link OrderedCompatibilityRule}.
     *
     * @param <U> T, as a Comparable
     * @return the added ordered check rule
     */
    // U extends T
    <U extends Comparable<U>> OrderedCompatibilityRule<U> addOrderedCheck();

    /**
     * Adds an arbitrary compatibility rule to the chain.
     *
     * @param rule the rule to add to the chain
     */
    void add(CompatibilityRule<T> rule);

    /**
     * Replaces the list of rules with the provided list of rules.
     *
     * @param rules the rule chain
     */
    void setRules(List<CompatibilityRule<T>> rules);

    /**
     * Tells that if no rule expressed an opinion about compatibility of values, then they are
     * deemed incompatible (this is the default).
     */
    void eventuallyIncompatible();

    /**
     * Tells that if no rule expressed an opinion about compatibility of values, then they are
     * deemed compatible.
     */
    void eventuallyCompatible();
}
