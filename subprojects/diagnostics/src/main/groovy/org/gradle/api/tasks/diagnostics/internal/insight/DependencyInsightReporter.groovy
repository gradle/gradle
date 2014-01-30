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

package org.gradle.api.tasks.diagnostics.internal.insight

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.*

/**
 * Created: 23/08/2012
 */
public class DependencyInsightReporter {

    Collection<RenderableDependency> prepare(Collection<DependencyResult> input, VersionMatcher versionMatcher) {
        def out = new LinkedList<RenderableDependency>()
        def dependencies = input.collect {
            if (it instanceof UnresolvedDependencyResult) {
                return new UnresolvedDependencyEdge(it)
            } else {
                return new ResolvedDependencyEdge(it)
            }
        }

        def sorted = DependencyResultSorter.sort(dependencies, versionMatcher)

        //remember if module id was annotated
        def annotated = new HashSet<ComponentIdentifier>()
        def current = null

        for (DependencyEdge dependency: sorted) {
            //add description only to the first module
            if (annotated.add(dependency.actual)) {
                //add a heading dependency with the annotation if the dependency does not exist in the graph
                if (!dependency.requested.matchesStrictly(dependency.actual)) {
                    out << new DependencyReportHeader(dependency)
                    current = new RequestedVersion(dependency.requested, dependency.actual, dependency.resolvable, null)
                    out << current
                } else {
                    current = new RequestedVersion(dependency.requested, dependency.actual, dependency.resolvable, getReasonDescription(dependency.reason))
                    out << current
                }
            } else if (current.requested != dependency.requested) {
                current = new RequestedVersion(dependency.requested, dependency.actual, dependency.resolvable, null)
                out << current
            }
            current.addChild(dependency)
        }

        out
    }

    private String getReasonDescription(ComponentSelectionReason reason) {
        !reason.expected ? reason.description : null
    }
}