/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.compare.internal;

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociation;

/**
 * Convenience base class for BuildOutcomeComparisonResult implementations.
 *
 * @param <T> The type of the outcome the result is for.
 */
public abstract class BuildOutcomeComparisonResultSupport<T extends BuildOutcome> implements BuildOutcomeComparisonResult<T> {

    private final BuildOutcomeAssociation<T> compared;

    protected BuildOutcomeComparisonResultSupport(BuildOutcomeAssociation<T> compared) {
        this.compared = compared;
    }

    @Override
    public BuildOutcomeAssociation<T> getCompared() {
        return compared;
    }
}
