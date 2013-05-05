/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ConflictResolution;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;

public interface ResolutionStrategyInternal extends ResolutionStrategy {

    /**
     * Gets the current expiry policy for dynamic revisions.
     *
     * @return the expiry policy
     */
    CachePolicy getCachePolicy();

    /**
     * Until the feature 'settles' and we receive more feedback, it's internal
     *
     * @return conflict resolution
     */
    ConflictResolution getConflictResolution();

    /**
     * The nascent DSL for cache control, and possibly other per-module resolution overrides
     * @return the resolution rules
     */
    ResolutionRules getResolutionRules();

    /**
     * @return the dependency resolve rule (may aggregate multiple rules)
     */
    Action<DependencyResolveDetailsInternal> getDependencyResolveRule();

    /**
     * @return copy of this resolution strategy. See the contract of {@link org.gradle.api.artifacts.Configuration#copy()}.
     */
    ResolutionStrategyInternal copy();
}
