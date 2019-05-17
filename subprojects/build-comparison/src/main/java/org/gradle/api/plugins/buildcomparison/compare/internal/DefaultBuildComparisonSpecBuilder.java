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

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociation;
import org.gradle.api.plugins.buildcomparison.outcome.internal.DefaultBuildOutcomeAssociation;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DefaultBuildComparisonSpecBuilder implements BuildComparisonSpecBuilder {

    private final Set<BuildOutcome> source = new HashSet<BuildOutcome>();
    private final Set<BuildOutcome> target = new HashSet<BuildOutcome>();
    private final List<BuildOutcomeAssociation<?>> outcomeAssociations = new LinkedList<BuildOutcomeAssociation<?>>();

    @Override
    public <A extends BuildOutcome, F extends A, T extends A> BuildOutcomeAssociation<A> associate(F from, T to, Class<A> type) {
        this.source.add(from);
        this.target.add(to);

        BuildOutcomeAssociation<A> outcomeAssociation = new DefaultBuildOutcomeAssociation<A>(from, to, type);
        outcomeAssociations.add(outcomeAssociation);

        return outcomeAssociation;
    }

    @Override
    public <F extends BuildOutcome> void addUnassociatedFrom(F from) {
        this.source.add(from);
    }

    @Override
    public <T extends BuildOutcome> void addUnassociatedTo(T to) {
        this.target.add(to);
    }

    @Override
    public BuildComparisonSpec build() {
        return new DefaultBuildComparisonSpec(source, target, outcomeAssociations);
    }
}
