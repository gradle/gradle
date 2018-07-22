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
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CompositeVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.InverseVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

import java.util.Collection;
import java.util.List;
import java.util.Set;

abstract class MessageBuilderHelper {
    static Collection<String> pathTo(EdgeState edge) {
        return pathTo(edge, true);
    }

    static Collection<String> pathTo(EdgeState edge, boolean includeLast) {
        List<List<EdgeState>> acc = Lists.newArrayListWithExpectedSize(1);
        pathTo(edge, Lists.<EdgeState>newArrayList(), acc, Sets.<NodeState>newHashSet());
        List<String> result = Lists.newArrayListWithCapacity(acc.size());
        for (List<EdgeState> path : acc) {
            EdgeState target = Iterators.getLast(path.iterator());
            StringBuilder sb = new StringBuilder();
            if (target.getSelector().getDependencyMetadata().isPending()) {
                sb.append("Constraint path ");
            } else {
                sb.append("Dependency path ");
            }
            boolean first = true;
            for (EdgeState e : path) {
                if (!first) {
                    sb.append(" --> ");
                }
                first = false;
                ModuleVersionIdentifier id = e.getFrom().getResolvedConfigurationId().getId();
                sb.append('\'').append(id).append('\'');
            }
            if (includeLast) {
                sb.append(" --> ");
                ModuleIdentifier moduleId = edge.getSelector().getTargetModule().getId();
                sb.append('\'').append(moduleId.getGroup()).append(':').append(moduleId.getName()).append('\'');
            }
            result.add(sb.toString());
        }
        return result;
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

    static String renderVersionConstraint(ResolvedVersionConstraint constraint) {
        if (constraint.isRejectAll()) {
            return "rejects all versions";
        }
        VersionSelector preferredSelector = constraint.getPreferredSelector();
        VersionSelector rejectedSelector = constraint.getRejectedSelector();
        if (rejectedSelector == null) {
            return "prefers '" + preferredSelector.getSelector() + "'";
        }
        if (rejectedSelector instanceof InverseVersionSelector) {
            return "strictly '" + preferredSelector.getSelector() + "'";
        }

        StringBuilder sb = new StringBuilder("prefers '");
        sb.append(preferredSelector.getSelector());
        sb.append("', rejects ");
        if (rejectedSelector instanceof CompositeVersionSelector) {
            sb.append("any of \"");
            int i = 0;
            for (VersionSelector selector : ((CompositeVersionSelector) rejectedSelector).getSelectors()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append('\'');
                sb.append(selector.getSelector());
                sb.append('\'');
            }
            sb.append("\"");
        } else {
            sb.append('\'');
            sb.append(rejectedSelector.getSelector());
            sb.append('\'');
        }
        return sb.toString();
    }
}
