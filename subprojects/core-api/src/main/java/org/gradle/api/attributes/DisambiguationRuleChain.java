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

import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.internal.HasInternalProtocol;

import java.util.Comparator;

/**
 * <p>A chain of disambiguation rules. By default the chain is empty and will not do any disambiguation.</p>
 *
 * <p>For a given set of rules, the execution is done <i>in order</i>, and interrupts as soon as a rule
 * selected at least one candidate (through {@link MultipleCandidatesDetails#closestMatch(Object)}).
 * </p>
 *
 * <p>If the end of the rule chain is reached and that no rule selected a candidate then the candidate list is returned
 * unmodified, meaning we still have an ambiguous match.</p>
 *
 * @param <T> the concrete type of the attribute
 */
@HasInternalProtocol
public interface DisambiguationRuleChain<T> {

    /**
     * <p>Adds an arbitrary disambiguation rule to the chain.</p>
     * <p>A disambiguation rule can select the best match from a list of candidates.</p>
     *
     * <p>A rule <i>can</i> express an preference by calling the {@link MultipleCandidatesDetails#closestMatch(Object)}
     * method to tell that a candidate is the best one.</p>
     *
     * <p>It is not mandatory for a rule to choose, and it is not an error to select multiple candidates.</p>
     *
     * @param rule the rule to add
     * @since 4.0
     */
    void add(Class<? extends AttributeDisambiguationRule<T>> rule);

    /**
     * <p>Adds an arbitrary disambiguation rule to the chain, possibly configuring the rule as well.</p>
     *
     * @param rule the rule to add
     * @param configureAction the action to use to configure the rule
     * @since 4.0
     */
    void add(Class<? extends AttributeDisambiguationRule<T>> rule, Action<? super ActionConfiguration> configureAction);

    /**
     * Adds an ordered disambiguation rule. Values will be compared using the
     * provided comparator, and the rule will automatically select the first
     * value (if multiple candidates have the same attribute value, there will
     * still be an ambiguity).
     *
     * @param comparator the comparator to use
     */
    void pickFirst(Comparator<? super T> comparator);

    /**
     * Adds an ordered disambiguation rule. Values will be compared using the
     * provided comparator, and the rule will automatically select the last
     * value (if multiple candidates have the same attribute value, there will
     * still be an ambiguity).
     *
     * @param comparator the comparator to use
     */
    void pickLast(Comparator<? super T> comparator);

}
