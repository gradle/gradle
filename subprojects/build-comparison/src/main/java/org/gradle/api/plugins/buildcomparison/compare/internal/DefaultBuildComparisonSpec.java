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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultBuildComparisonSpec implements BuildComparisonSpec {

    private final Set<BuildOutcome> source;
    private final Set<BuildOutcome> target;
    private final List<BuildOutcomeAssociation<?>> outcomeAssociations;

    public DefaultBuildComparisonSpec(Set<BuildOutcome> source, Set<BuildOutcome> target, List<BuildOutcomeAssociation<?>> outcomeAssociations) {
        this.source = Collections.unmodifiableSet(new HashSet<>(source));
        this.target = Collections.unmodifiableSet(new HashSet<>(target));
        this.outcomeAssociations = Collections.unmodifiableList(new ArrayList<>(outcomeAssociations));
    }

    @Override
    public Set<BuildOutcome> getSource() {
        return source;
    }

    @Override
    public Set<BuildOutcome> getTarget() {
        return target;
    }

    @Override
    public List<BuildOutcomeAssociation<?>> getOutcomeAssociations() {
        return outcomeAssociations;
    }
}
