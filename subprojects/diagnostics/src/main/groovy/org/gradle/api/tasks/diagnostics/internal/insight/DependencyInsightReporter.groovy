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

package org.gradle.api.tasks.diagnostics.internal.insight;


import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.InvertedRenderableDependencyResult
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.SimpleDependency

/**
 * Created: 23/08/2012
 *
 * @author Szczepan Faber
 */
public class DependencyInsightReporter {

    Collection<RenderableDependency> prepare(Collection<ResolvedDependencyResult> input) {
        def out = new LinkedList<RenderableDependency>()
        def sorted = ResolvedDependencyResultSorter.sort(input)

        //remember if module id was annotated
        def annotated = new HashSet<ModuleVersionIdentifier>()

        for (ResolvedDependencyResult dependency: sorted) {
            def description = null
            //add description only to the first module
            if (annotated.add(dependency.selected.id)) {
                //add a heading dependency with the annotation if the dependency does not exist in the graph
                if (!dependency.requested.matchesStrictly(dependency.selected.id)) {
                    def name = dependency.selected.id.group + ":" + dependency.selected.id.name + ":" + dependency.selected.id.version
                    out << new SimpleDependency(name, describeReason(dependency.selected.selectionReason))
                } else {
                    description = describeReason(dependency.selected.selectionReason)
                }
            }

            out << new InvertedRenderableDependencyResult(dependency, description)
        }

        out
    }

    private String describeReason(ModuleVersionSelectionReason reason) {
        if (reason.conflictResolution || reason.forced) {
            return reason.description
        } else {
            return null
        }
    }
}