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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

abstract class MessageBuilderHelper {
    private static boolean isDependencyForced(DependencyMetadata dependency) {
        return dependency instanceof ForcingDependencyMetadata && ((ForcingDependencyMetadata) dependency).isForce();
    }

    static Collection<String> pathTo(EdgeState edge) {
        return pathTo(edge, true);
    }

    static Collection<String> pathTo(EdgeState edge, boolean includeLast) {
        List<List<EdgeState>> acc = Lists.newArrayListWithExpectedSize(1);
        pathTo(edge, Lists.newArrayList(), acc, Sets.newHashSet());
        List<String> result = Lists.newArrayListWithCapacity(acc.size());
        for (List<EdgeState> path : acc) {
            EdgeState target = Iterators.getLast(path.iterator());
            StringBuilder sb = new StringBuilder();
            if (target.getSelector().getDependencyMetadata().isConstraint()) {
                sb.append("Constraint path ");
            } else {
                sb.append("Dependency path ");
            }
            boolean first = true;
            String variantDetails = null;
            for (EdgeState e : path) {
                if (!first) {
                    sb.append(" --> ");
                }
                first = false;
                ModuleVersionIdentifier id = e.getFrom().getResolvedConfigurationId().getId();
                sb.append('\'').append(id).append('\'');
                if (variantDetails != null) {
                    sb.append(variantDetails);
                }
                variantDetails = variantDetails(e);
            }
            if (includeLast) {
                sb.append(" --> ");
                SelectorState selector = edge.getSelector();
                ModuleIdentifier moduleId = selector.getTargetModule().getId();
                sb.append('\'').append(moduleId.getGroup()).append(':').append(moduleId.getName()).append('\'');
                if (variantDetails != null) {
                    sb.append(variantDetails);
                }
            }
            result.add(sb.toString());
        }
        return result;
    }

    @Nullable
    private static String variantDetails(EdgeState e) {
        ResolvedVariantResult selectedVariant = e.hasSelectedVariant() ? e.getSelectedNode().getResolveState().getVariantResult(null) : null;
        if (selectedVariant != null) {
            return " (" + selectedVariant.getDisplayName() + ")";
        }
        return null;
    }

    static void pathTo(EdgeState component, List<EdgeState> currentPath, List<List<EdgeState>> accumulator, Set<NodeState> alreadySeen) {
        if (alreadySeen.add(component.getFrom())) {
            currentPath.add(0, component);
            for (EdgeState dependent : component.getFrom().getIncomingEdges()) {
                List<EdgeState> otherPath = Lists.newArrayList(currentPath);
                pathTo(dependent, otherPath, accumulator, alreadySeen);
            }
            if (component.getFrom().isRoot()) {
                accumulator.add(currentPath);
            }
        }
    }
}
