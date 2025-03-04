/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public interface ResolvedVersionConstraint {
    @Nullable
    VersionSelector getPreferredSelector();

    @Nullable
    VersionSelector getRequiredSelector();

    @Nullable
    VersionSelector getRejectedSelector();

    /**
     * Gets all selectors in {@link #getPreferredSelector()}, {@link #getRequiredSelector()}, {@link #getRejectedSelector()}
     * that are non-{@code null}.
     *
     * @return set of all selectors that are non-{@code null}
     */
    default Set<VersionSelector> getSelectors() {
        Set<VersionSelector> result = new HashSet<>(3);
        if (getPreferredSelector() != null) {
            result.add(getPreferredSelector());
        } else if (getRequiredSelector() != null) {
            result.add(getRequiredSelector());
        } else if (getRejectedSelector() != null) {
            result.add(getRejectedSelector());
        }
        return result;
    }

    boolean isRejectAll();
    boolean isDynamic();
    boolean isStrict();

    boolean accepts(String version);

    boolean canBeStable();
}
