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
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.util.List;
import java.util.Set;

/**
 * The result of a comparison between two sets of build outcomes.
 */
public class BuildComparisonResult {

    private List<BuildOutcomeComparisonResult<?>> comparisons;
    private Set<BuildOutcome> uncomparedSourceOutcomes;
    private Set<BuildOutcome> uncomparedTargetOutcomes;


    public BuildComparisonResult(Set<BuildOutcome> uncomparedSourceOutcomes, Set<BuildOutcome> uncomparedTargetOutcomes, List<BuildOutcomeComparisonResult<?>> comparisons) {
        this.comparisons = comparisons;
        this.uncomparedSourceOutcomes = uncomparedSourceOutcomes;
        this.uncomparedTargetOutcomes = uncomparedTargetOutcomes;
    }

    public List<BuildOutcomeComparisonResult<?>> getComparisons() {
        return comparisons;
    }

    public Set<BuildOutcome> getUncomparedSourceOutcomes() {
        return uncomparedSourceOutcomes;
    }

    public Set<BuildOutcome> getUncomparedTargetOutcomes() {
        return uncomparedTargetOutcomes;
    }

    public boolean isBuildsAreIdentical() {
        //noinspection SimplifiableIfStatement
        if (!getUncomparedSourceOutcomes().isEmpty() || !getUncomparedTargetOutcomes().isEmpty()) {
            return false;
        } else {
            return CollectionUtils.every(comparisons, new Spec<BuildOutcomeComparisonResult<?>>() {
                public boolean isSatisfiedBy(BuildOutcomeComparisonResult<?> comparisonResult) {
                    return comparisonResult.isOutcomesAreIdentical();
                }
            });
        }
    }
}
