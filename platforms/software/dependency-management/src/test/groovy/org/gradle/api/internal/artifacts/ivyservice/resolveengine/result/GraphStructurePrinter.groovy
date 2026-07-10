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

class GraphStructurePrinter {

    private static void printEdge(GraphStructure structure, int edgeIndex, StringBuilder sb, Set<Integer> visited, String indent) {
        def edges = structure.edges()
        def node = edges.targetNode(edgeIndex)
        if (node == -1) {
            def failure = edges.failure(edgeIndex).failure()
            def selector = edges.selector(edgeIndex)
            sb.append(indent + selector + " -> " + failure.selector + " - " + failure.message + "\n");
            return
        }

        def firstVisit = visited.add(node)

        def component = structure.nodes().owner(node)
        def reason = structure.components().selectionReason(component).conflictResolution? "(C)" : "";
        def requested = edges.selector(edgeIndex)
        def truncated =  !firstVisit ? " (*)" : ""
        sb.append(indent + requested + truncated + reason + "\n");

        if (!firstVisit) {
            return
        }

        for (int i = edges.start(node); i < edges.end(node); i++) {
            printEdge(structure, i, sb, visited, "  " + indent);
        }
    }

    static String printGraph(GraphStructure structure) {
        StringBuilder sb = new StringBuilder()

        def rootNode = structure.nodes().root()
        def visited = new HashSet()
        visited.add(rootNode)

        sb.append(structure.components().id(structure.nodes().owner(rootNode))).append("\n")
        for (int i = structure.edges().start(rootNode); i < structure.edges().end(rootNode); i++) {
            printEdge(structure, i, sb, visited, "  ")
        }

        sb.toString()
    }

}
