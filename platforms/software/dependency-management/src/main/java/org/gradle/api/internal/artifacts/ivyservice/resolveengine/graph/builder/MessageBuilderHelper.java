/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract class MessageBuilderHelper {

    public static List<String> formattedPathsTo(EdgeState edge) {
        return findPathsTo(edge).stream().map(path -> {
            String header = Iterables.getLast(path).getSelector().getDependencyMetadata().isConstraint() ? "Constraint" : "Dependency";
            String formattedPath = path.stream()
                .map(EdgeState::getFrom)
                .map(node -> {
                    String id = node.getComponent().getComponentId().getDisplayName();
                    return "'" + id + "' (" + node.getMetadata().getName() + ")";
                })
                .collect(Collectors.joining(" --> "));

            return header + " path: " + formattedPath;
        }).collect(Collectors.toList());
    }

    private static List<List<EdgeState>> findPathsTo(EdgeState edge) {
        List<List<EdgeState>> acc = new ArrayList<>(1);
        pathTo(edge, new ArrayList<>(), acc, new HashSet<>());
        return acc;
    }

    private static void pathTo(EdgeState edge, List<EdgeState> currentPath, List<List<EdgeState>> accumulator, Set<NodeState> alreadySeen) {
        NodeState from = edge.getFrom();
        if (alreadySeen.add(from)) {
            currentPath.add(edge);

            List<EdgeState> incomingEdges = from.getIncomingEdges();
            if (!incomingEdges.isEmpty()) {
                for (EdgeState dependent : incomingEdges) {
                    List<EdgeState> otherPath = new ArrayList<>(currentPath);
                    pathTo(dependent, otherPath, accumulator, alreadySeen);
                }
            } else {
                // We've hit the root of the path
                accumulator.add(Lists.reverse(currentPath));
            }
        }
    }
}
