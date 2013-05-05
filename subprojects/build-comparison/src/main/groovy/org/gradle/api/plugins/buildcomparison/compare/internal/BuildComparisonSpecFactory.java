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
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociator;

import java.util.HashSet;
import java.util.Set;

public class BuildComparisonSpecFactory {

    private final BuildOutcomeAssociator associator;

    public BuildComparisonSpecFactory(BuildOutcomeAssociator associator) {
        this.associator = associator;
    }

    public BuildComparisonSpec createSpec(Set<BuildOutcome> from, Set<BuildOutcome> to) {
        BuildComparisonSpecBuilder builder = new DefaultBuildComparisonSpecBuilder();

        Set<BuildOutcome> toCopy = new HashSet<BuildOutcome>(to);

        for (BuildOutcome fromBuildOutcome : from) {
            BuildOutcome toBuildOutcome = null;
            Class<? extends BuildOutcome> associationType = null;

            for (BuildOutcome buildOutcome : toCopy) {
                toBuildOutcome = buildOutcome;
                associationType = associator.findAssociationType(fromBuildOutcome, toBuildOutcome);

                if (associationType != null) {
                    break;
                }
            }

            if (associationType == null) {
                builder.addUnassociatedFrom(fromBuildOutcome);
            } else {
                builder.associate(associationType.cast(fromBuildOutcome), associationType.cast(toBuildOutcome), (Class<BuildOutcome>)associationType);
                toCopy.remove(toBuildOutcome);
            }
        }

        for (BuildOutcome buildOutcome : toCopy) {
            builder.addUnassociatedTo(buildOutcome);
        }

        return builder.build();
    }
}
