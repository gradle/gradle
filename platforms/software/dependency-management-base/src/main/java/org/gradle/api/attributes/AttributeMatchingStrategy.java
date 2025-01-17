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

import java.util.Comparator;

/**
 * An attribute matching strategy is responsible for providing information about how an {@link Attribute}
 * is matched during dependency resolution. In particular, it will tell if a value, provided by a consumer,
 * is compatible with a value provided by a candidate.
 *
 * @param <T> the type of the attribute
 * @since 3.3
 */
public interface AttributeMatchingStrategy<T> {
    CompatibilityRuleChain<T> getCompatibilityRules();

    DisambiguationRuleChain<T> getDisambiguationRules();

    /**
     * <p>A short-hand way to define both a compatibility rule and
     * a disambiguation rule based on an order defined by the provided
     * {@link Comparator}.</p>
     *
     * <p>All provider values which are lower than or equal the consumer value are
     * compatible. When disambiguating, it will pick the highest compatible value.</p>
     *
     * @param comparator the comparator to use for compatibility and disambiguation
     */
    void ordered(Comparator<T> comparator);

    /**
     * <p>A short-hand way to define both a compatibility rule and
     * a disambiguation rule based on an order defined by the provided
     * {@link Comparator}.</p>
     *
     * <p>All provider values which are lower than or equal the consumer value are
     * compatible.</p>
     *
     * @param pickLast tells if, for disambiguation, we should pick the last value in order instead of the first one
     * @param comparator the comparator to use for compatibility and disambiguation
     */
    void ordered(boolean pickLast, Comparator<T> comparator);
}
