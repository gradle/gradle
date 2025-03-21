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

/* package */ abstract class MessageBuilderHelper {
    public static final String PATH_SEPARATOR = " --> ";

    private MessageBuilderHelper() { /* not instantiable */ }

    /* package */ static List<String> pathTo(EdgeState edge, boolean includeLast) {
        List<List<EdgeState>> acc = new ArrayList<>(1);
        pathTo(edge, new ArrayList<>(), acc, new HashSet<>());
        List<String> result = new ArrayList<>(acc.size());
        for (List<EdgeState> path : acc) {
            List<String> segmentedPathTo = segmentedPathTo(edge, includeLast, path);

            EdgeState target = Iterators.getLast(path.iterator());
            StringBuilder sb = new StringBuilder();
            if (target.getSelector().getDependencyMetadata().isConstraint()) {
                sb.append("Constraint path ");
            } else {
                sb.append("Dependency path ");
            }

            boolean first = true;
            for (String segment : segmentedPathTo) {
                if (!first) {
                    sb.append(PATH_SEPARATOR);
                }
                first = false;
                sb.append(segment);
            }

            result.add(sb.toString());
        }
        return result;
    }

    /* package */ static List<List<String>> segmentedPathsTo(EdgeState edge, boolean includeLast) {
        List<List<EdgeState>> acc = new ArrayList<>(1);
        pathTo(edge, new ArrayList<>(), acc, new HashSet<>());
        List<List<String>> result = new ArrayList<>(acc.size());
        for (List<EdgeState> path : acc) {
            List<String> currentPath = segmentedPathTo(edge, includeLast, path);
            result.add(currentPath);
        }
        return result;
    }

    private static List<String> segmentedPathTo(EdgeState edge, boolean includeLast, List<EdgeState> path) {
        List<String> currentPath = new ArrayList<>(path.size());
        String variantDetails = null;
        for (EdgeState e : path) {
            ModuleVersionIdentifier id = e.getFrom().getComponent().getModuleVersion();
            String currentSegment = "'" + id + "'";
            if (variantDetails != null) {
                currentSegment += variantDetails;
            }
            variantDetails = variantDetails(e);
            currentPath.add(currentSegment);
        }
        if (includeLast) {
            SelectorState selector = edge.getSelector();
            ModuleIdentifier moduleId = selector.getTargetModule().getId();
            String lastSegment = "'" + moduleId.getGroup()+ ":" + moduleId.getName() + "'";
            if (variantDetails != null) {
                lastSegment += variantDetails;
            }
            currentPath.add(lastSegment);
        }
        return currentPath;
    }

    @Nullable
    private static String variantDetails(EdgeState e) {
        String selectedVariantName = e.hasSelectedVariant() ? e.getSelectedNode().getMetadata().getName() : null;
        if (selectedVariantName != null) {
            return " (" + selectedVariantName + ")";
        }
        return null;
    }

    /* package */ static void pathTo(EdgeState component, List<EdgeState> currentPath, List<List<EdgeState>> accumulator, Set<NodeState> alreadySeen) {
        if (alreadySeen.add(component.getFrom())) {
            currentPath.add(0, component);
            for (EdgeState dependent : component.getFrom().getIncomingEdges()) {
                List<EdgeState> otherPath = new ArrayList<>(currentPath);
                pathTo(dependent, otherPath, accumulator, alreadySeen);
            }
        }
    }
}
