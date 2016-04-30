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

package org.gradle.api.tasks.diagnostics.internal.insight;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyReportHeader;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RequestedVersion;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.ResolvedDependencyEdge;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvedDependencyEdge;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class DependencyInsightReporter {
    public Collection<RenderableDependency> prepare(Collection<DependencyResult> input, VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator) {
        LinkedList<RenderableDependency> out = new LinkedList<RenderableDependency>();
        List<DependencyEdge> dependencies = CollectionUtils.collect(input, new Transformer<DependencyEdge, DependencyResult>() {
            @Override
            public DependencyEdge transform(DependencyResult result) {
                if (result instanceof UnresolvedDependencyResult) {
                    return new UnresolvedDependencyEdge((UnresolvedDependencyResult) result);
                } else {
                    return new ResolvedDependencyEdge((ResolvedDependencyResult) result);
                }
            }
        });
        Collection<DependencyEdge> sorted = DependencyResultSorter.sort(dependencies, versionSelectorScheme, versionComparator);

        //remember if module id was annotated
        HashSet<ComponentIdentifier> annotated = new HashSet<ComponentIdentifier>();
        RequestedVersion current = null;

        for (DependencyEdge dependency : sorted) {
            //add description only to the first module
            if (annotated.add(dependency.getActual())) {
                //add a heading dependency with the annotation if the dependency does not exist in the graph
                if (!dependency.getRequested().matchesStrictly(dependency.getActual())) {
                    out.add(new DependencyReportHeader(dependency));
                    current = new RequestedVersion(dependency.getRequested(), dependency.getActual(), dependency.isResolvable(), null);
                    out.add(current);
                } else {
                    current = new RequestedVersion(dependency.getRequested(), dependency.getActual(), dependency.isResolvable(), getReasonDescription(dependency.getReason()));
                    out.add(current);
                }
            } else if (!current.getRequested().equals(dependency.getRequested())) {
                current = new RequestedVersion(dependency.getRequested(), dependency.getActual(), dependency.isResolvable(), null);
                out.add(current);
            }

            current.addChild(dependency);
        }

        return out;
    }

    private String getReasonDescription(ComponentSelectionReason reason) {
        return !reason.isExpected() ? reason.getDescription() : null;
    }

}
