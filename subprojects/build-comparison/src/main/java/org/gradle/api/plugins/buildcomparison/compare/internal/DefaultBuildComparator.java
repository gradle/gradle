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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DefaultBuildComparator implements BuildComparator {

    BuildOutcomeComparatorFactory comparatorFactory;

    public DefaultBuildComparator(BuildOutcomeComparatorFactory comparatorFactory) {
        this.comparatorFactory = comparatorFactory;
    }

    @Override
    public BuildComparisonResult compareBuilds(BuildComparisonSpec spec) {
        Set<BuildOutcome> uncomparedFrom = new HashSet<BuildOutcome>(spec.getSource());
        Set<BuildOutcome> uncomparedTo = new HashSet<BuildOutcome>(spec.getTarget());

        Set<BuildOutcome> unknownFrom = new HashSet<BuildOutcome>();
        Set<BuildOutcome> unknownTo = new HashSet<BuildOutcome>();

        List<BuildOutcomeComparisonResult<?>> results = new LinkedList<BuildOutcomeComparisonResult<?>>();

        for (BuildOutcomeAssociation<? extends BuildOutcome> outcomeAssociation : spec.getOutcomeAssociations()) {
            BuildOutcome from = outcomeAssociation.getSource();
            boolean unknown = false;

            if (!uncomparedFrom.remove(from)) {
                unknown = true;
                unknownFrom.add(from);
            }

            BuildOutcome to = outcomeAssociation.getTarget();
            if (!uncomparedTo.remove(to)) {
                unknown = true;
                unknownTo.add(to);
            }

            // TODO - error if there are unknowns?
            if (!unknown) {
                BuildOutcomeComparator<?, ?> comparator = comparatorFactory.getComparator(outcomeAssociation.getType());
                if (comparator == null) {
                    // TODO - better exception
                    throw new RuntimeException(String.format("No comparator for %s", outcomeAssociation.getType()));
                }

                @SuppressWarnings("unchecked")
                BuildOutcomeComparisonResult<?> comparisonResult = (BuildOutcomeComparisonResult<?>) comparator.compare((BuildOutcomeAssociation) outcomeAssociation);

                results.add(comparisonResult);
            }
        }

        return new BuildComparisonResult(uncomparedFrom, uncomparedTo, results);
    }

}
