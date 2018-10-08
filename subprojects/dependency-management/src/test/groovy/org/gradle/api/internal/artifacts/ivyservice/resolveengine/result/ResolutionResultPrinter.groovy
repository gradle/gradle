/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

public class ResolutionResultPrinter {
    private static void printNode(DependencyResult dep, StringBuilder sb, Set visited, String indent) {
        if (dep instanceof UnresolvedDependencyResult) {
            sb.append(indent + dep + "\n");
            return
        }
        if (!visited.add(dep.getSelected())) {
            return
        }
        String reason = dep.selected.selectionReason.conflictResolution? "(C)" : "";
        sb.append(indent + dep + reason + " [" + dep.selected.dependents*.from.id.module.join(",") + "]\n");
        for (DependencyResult d : dep.getSelected().getDependencies()) {
            printNode(d, sb, visited, "  " + indent);
        }
    }

    static String printGraph(ResolvedComponentResult root) {
        StringBuilder sb = new StringBuilder();
        sb.append(root).append("\n");
        for (DependencyResult d : root.getDependencies()) {
            printNode(d, sb, new HashSet(), "  ");
        }

        sb.toString();
    }
}
