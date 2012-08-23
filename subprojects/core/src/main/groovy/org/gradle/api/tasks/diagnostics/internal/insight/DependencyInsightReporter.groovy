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
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.dependencies.LatestRevisionSelector
import org.gradle.api.internal.artifacts.result.ResolvedDependencyResultSorter
import org.gradle.api.tasks.diagnostics.internal.dependencies.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.dependencies.RenderableDependencyResult
import org.gradle.api.tasks.diagnostics.internal.dependencies.SimpleDependency

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

/**
 * Created: 23/08/2012
 *
 * @author Szczepan Faber
 */
public class DependencyInsightReporter {

    Collection<RenderableDependency> prepare(Collection<ResolvedDependencyResult> input) {
        def out = new LinkedList<RenderableDependency>()
        def sorted = ResolvedDependencyResultSorter.sort(input)

        //accumulate all candidate versions for given module id
        def versions = new HashMap<ModuleVersionIdentifier, List<String>>()
        for (ResolvedDependencyResult dependency: sorted) {
            if (versions[dependency.selected.id]) {
                versions[dependency.selected.id] += dependency.requested.version
            } else {
                versions[dependency.selected.id] = [dependency.requested.version]
            }
        }

        //remember if module id was annotated
        def annotated = new HashSet<ModuleVersionIdentifier>()

        for (ResolvedDependencyResult dependency: sorted) {
            def description = ""
            //add description only to the first module
            if (annotated.add(dependency.selected.id)) {
                //inform the module version not requested in the graph
                if (newId(dependency.requested) != dependency.selected.id) {
                    def name = dependency.selected.id.group + ":" + dependency.selected.id.name + ":" + dependency.selected.id.version
                    out << new SimpleDependency(name, " (not requested)")
                //inform the module version is not the latest
                } else if (new LatestRevisionSelector().latest(versions[dependency.selected.id]) != dependency.selected.id.version) {
                    //TODO SF I'm not quite sure about this feature
                    description = " (not newest)"
                }
            }

            out << new RenderableDependencyResult(dependency, description)
        }

        out
    }
}