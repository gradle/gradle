/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CompositeResolvedArtifactSet implements ResolvedArtifactSet {
    private final List<ResolvedArtifactSet> sets;

    private CompositeResolvedArtifactSet(List<ResolvedArtifactSet> sets) {
        this.sets = sets;
    }

    public static ResolvedArtifactSet of(Collection<? extends ResolvedArtifactSet> sets) {
        List<ResolvedArtifactSet> filtered = new ArrayList<>(sets.size());
        for (ResolvedArtifactSet set : sets) {
            if (set != ResolvedArtifactSet.EMPTY) {
                filtered.add(set);
            }
        }
        if (filtered.isEmpty()) {
            return EMPTY;
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return new CompositeResolvedArtifactSet(filtered);
    }

    @Override
    public void visit(Visitor visitor) {
        for (ResolvedArtifactSet set : sets) {
            set.visit(visitor);
        }
    }

    @Override
    public void visitTransformSources(TransformSourceVisitor visitor) {
        for (ResolvedArtifactSet set : sets) {
            set.visitTransformSources(visitor);
        }
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        for (ResolvedArtifactSet set : sets) {
            set.visitExternalArtifacts(visitor);
        }
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        for (ResolvedArtifactSet set : sets) {
            set.visitDependencies(context);
        }
    }
}
