/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching;

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.internal.action.InstantiatingAction;

/**
 * A caching rule executor allows caching the result of processing a rule. A rule is represented by a
 * {@link InstantiatingAction<DETAILS> instantiating action}, which is responsible for instantiating
 * and configuring a rule. From a user point of view, a rule works on a {@link DETAILS} instance,
 * which is often, but not necessarily, a builder. A {@link DETAILS} can mutate an existing object,
 * for example. This means that the result of executing the rule is not necessarily the same as the
 * details object. It must be possible for the consumer (the object which executes the rule) to reproduce
 * the result of executing the rule from the {@link RESULT} object.
 *
 * The cache key consists of the provided key and the rule. This means that if the implementation of the
 * rule changes, or that its configuration changes, then we will have a cache miss.
 *
 * @param <KEY> the primary key for the cache. This is an explicit input, whereas the rule is an implicit one.
 * @param <DETAILS> the publicly exposed type that a user would see. Typically component metadata details.
 * @param <RESULT> the result of executing the rule, which may be the same as the DETAILS, but will often be
 * a different type that the caller can use to reproduce the execution of the rule. This object is the one
 * that is going to be cached, and most likely the thing the caller really cares about
 */
public interface CachingRuleExecutor<KEY, DETAILS, RESULT> {
    /**
     * Executes a rule, fetching the result from the cache if available, or executing the rule and caches
     * the result in case of cache miss.
     *  @param rule the rule to be executed
     * @param detailsToResult transforms a details object into a result object which may be cached
     * @param onCacheMiss whenever cache is missed, this function is responsible for creating the initial DETAILS object that is going to be exposed to the user
     * @param cachePolicy the cache policy
     */
    <D extends DETAILS> RESULT execute(
        KEY key,
        InstantiatingAction<DETAILS> rule,
        Transformer<RESULT, D> detailsToResult,
        Transformer<D, KEY> onCacheMiss,
        CachePolicy cachePolicy);
}
