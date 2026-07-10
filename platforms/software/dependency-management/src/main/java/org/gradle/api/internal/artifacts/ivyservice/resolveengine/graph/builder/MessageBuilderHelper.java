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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MessageBuilderHelper {

    private MessageBuilderHelper() { /* not instantiable */ }

    public static List<String> formattedPathsTo(DependencyGraphEdge edge) {
        return findPathsTo(edge).stream().map(path -> {
            String header = Iterables.getLast(path).getDependencyMetadata().isConstraint() ? "Constraint" : "Dependency";
            String formattedPath = streamNodeNames(path)
                .collect(Collectors.joining(" --> "));

            return header + " path: " + formattedPath;
        }).collect(Collectors.toList());
    }

    public static ImmutableList<ImmutableList<String>> findPathNamesTo(DependencyGraphEdge edge) {
        return findPathsTo(edge).stream()
            .map(p -> streamNodeNames(p).collect(ImmutableList.toImmutableList()))
            .collect(ImmutableList.toImmutableList());
    }

    public static List<List<DependencyGraphEdge>> findPathsTo(DependencyGraphEdge edge) {
        List<List<DependencyGraphEdge>> acc = new ArrayList<>(1);
        pathTo(edge, new ArrayList<>(), acc, new HashSet<>());
        return acc;
    }

    private static void pathTo(DependencyGraphEdge edge, List<DependencyGraphEdge> currentPath, List<List<DependencyGraphEdge>> accumulator, Set<DependencyGraphNode> alreadySeen) {
        DependencyGraphNode from = edge.getFrom();
        if (alreadySeen.add(from)) {
            currentPath.add(edge);

            Collection<? extends DependencyGraphEdge> incomingEdges = from.getIncomingEdges();
            if (!incomingEdges.isEmpty()) {
                for (DependencyGraphEdge dependent : incomingEdges) {
                    List<DependencyGraphEdge> otherPath = new ArrayList<>(currentPath);
                    pathTo(dependent, otherPath, accumulator, alreadySeen);
                }
            } else {
                // We've hit the root of the path
                accumulator.add(Lists.reverse(currentPath));
            }
        }
    }

    private static Stream<String> streamNodeNames(List<DependencyGraphEdge> path) {
        return path.stream().map(edge -> edge.getFrom().getDisplayName());
    }
}
