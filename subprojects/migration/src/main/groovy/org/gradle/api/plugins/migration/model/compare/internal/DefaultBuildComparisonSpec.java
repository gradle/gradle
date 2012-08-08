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

package org.gradle.api.plugins.migration.model.compare.internal;

import org.gradle.api.plugins.migration.model.association.BuildOutcomeAssociation;
import org.gradle.api.plugins.migration.model.association.internal.DefaultBuildOutcomeAssociation;
import org.gradle.api.plugins.migration.model.compare.BuildComparisonSpec;
import org.gradle.api.plugins.migration.model.outcome.BuildOutcome;

import java.util.*;

public class DefaultBuildComparisonSpec implements BuildComparisonSpec {

    private final Set<BuildOutcome> from = new HashSet<BuildOutcome>();
    private final Set<BuildOutcome> to = new HashSet<BuildOutcome>();
    private final List<BuildOutcomeAssociation<?>> outcomeAssociations = new LinkedList<BuildOutcomeAssociation<?>>();

    public Set<BuildOutcome> getFrom() {
        return Collections.unmodifiableSet(from);
    }

    public Set<BuildOutcome> getTo() {
        return Collections.unmodifiableSet(to);
    }

    public List<BuildOutcomeAssociation<?>> getOutcomeAssociations() {
        return Collections.unmodifiableList(outcomeAssociations);
    }

    public <A extends BuildOutcome, F extends A, T extends A> BuildOutcomeAssociation<A> associate(F from, T to, Class<A> type) {
        this.from.add(from);
        this.to.add(to);

        BuildOutcomeAssociation<A> outcomeAssociation = new DefaultBuildOutcomeAssociation<A>(from, to, type);
        outcomeAssociations.add(outcomeAssociation);

        return outcomeAssociation;
    }

}
