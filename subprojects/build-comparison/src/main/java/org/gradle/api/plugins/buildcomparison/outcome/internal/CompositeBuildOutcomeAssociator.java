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

package org.gradle.api.plugins.buildcomparison.outcome.internal;

import com.google.common.collect.Lists;

import java.util.List;

public class CompositeBuildOutcomeAssociator implements BuildOutcomeAssociator {

    private final List<BuildOutcomeAssociator> associators;

    public CompositeBuildOutcomeAssociator(Iterable<BuildOutcomeAssociator> associators) {
        this.associators = Lists.newLinkedList(associators);
    }

    @Override
    public Class<? extends BuildOutcome> findAssociationType(BuildOutcome source, BuildOutcome target) {
        for (BuildOutcomeAssociator associator : associators) {
            Class<? extends BuildOutcome> outcomeType = associator.findAssociationType(source, target);
            if (outcomeType != null) {
                return outcomeType;
            }
        }

        return null;
    }
}
