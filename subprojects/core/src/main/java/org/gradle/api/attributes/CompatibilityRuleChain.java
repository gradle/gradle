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
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import java.util.Comparator;

/**
 * <p>A chain of compatibility checks, implemented as action rules. By default
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
     * Adds an ordered check rule to this chain.
     *
     * @param comparator the comparator to use
     *
     * @return the added ordered check rule
     */
    void ordered(Comparator<? super T> comparator);

    /**
     * Adds an reverse ordered check rule to this chain.
     *
     * @param comparator the comparator to use
     *
     * @return the added ordered check rule
     */
    void reverseOrdered(Comparator<? super T> comparator);

    /**
     * <p>Adds an arbitrary compatibility rule to the chain.</p>
     * <p>A compatibility rule can tell if two {@link AttributeValue values} are compatible.
     * Compatibility doesn't mean equality. Typically two different Java platforms can be
     * compatible, without being equal.</p>
     *
     * <p>A rule <i>can</i> express an opinion by calling the @{link {@link CompatibilityCheckDetails#compatible()}}
     * method to tell that two attributes are compatible, or it <i>can</i> call {@link CompatibilityCheckDetails#incompatible()}
     * to say that they are not compatible. It is not mandatory for a rule to express an opinion.</p>
     *
     * @param rule the rule to add to the chain
     */
    void add(Action<? super CompatibilityCheckDetails<T>> rule);

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

    /**
     * Adds a rule that tells that if an attribute is found on the consumer but not on the producer,
     * the match is compatible.
     */
    void optionalOnProducer();

    /**
     * Adds a rule that tells that if an attribute is found on the producer but not on the consumer
     * the match is compatible.
     */
    void optionalOnConsumer();
}
