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
 * <p>A chain of {@link DisambiguationRule disambiguation rules}. By default
 * the chain is empty and will not do any disambiguation.</p>
 *
 * <p>For a given set of rules, the execution is done <i>in order</i>, and interrupts as soon as a rule
 * selected at least one candidate (through {@link MultipleCandidatesDetails#closestMatch(HasAttributes)}).
 * </p>
 *
 * <p>If the end of the rule chain is reached and that no rule selected a candidate then the candidate list is determined
 * based on the calls to {@link #eventuallySelectAll()} (no disambiguation) or {@link #eventuallySelectNone()} (none is best)</p>
 *
 * @param <T> the concrete type of the attribute
 */
@Incubating
@HasInternalProtocol
public interface DisambiguationRuleChain<T> {

    /**
     * Adds an arbitrary disambiguation rule to the chain.
     *
     * @param rule the rule to add
     */
    void add(DisambiguationRule<T> rule);

    /**
     * Adds an ordered disambiguation rule. This method can only be called if the type of the attribute
     * is {@link Comparable} and the semantics of the rule are described in {@link OrderedDisambiguationRule}.
     *
     * @param <U> T, as a Comparable
     * @return the added ordered disambiguation rule
     */
    // U extends T
    <U extends Comparable<U>> OrderedDisambiguationRule<U> addOrderedDisambiguation();

    /**
     * Replaces the current chain of rules with the provided rules.
     *
     * @param rules the new rule list
     */
    void setRules(List<DisambiguationRule<T>> rules);

    /**
     * Tells the engine not to disambiguate if no rule expressed a preference.
     */
    void eventuallySelectAll();

    /**
     * Tells the engine to fail if no rule expressed a preference.
     */
    void eventuallySelectNone();
}
