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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CompositeVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class RejectedModuleMessageBuilder {
    public String buildFailureMessage(ModuleResolveState module) {
        boolean hasRejectAll = false;
        for (SelectorState candidate : module.getSelectors()) {
            hasRejectAll |= isRejectAll(candidate.getVersionConstraint());
        }
        StringBuilder sb = new StringBuilder();
        if (hasRejectAll) {
            sb.append("Module ");
        } else {
            sb.append("Cannot find a version of ");
        }
        boolean first = true;
        for (EdgeState incomingEdge : getIncomingEdges(module)) {
            SelectorState selector = incomingEdge.getSelector();
            if (first) {
                sb.append("'").append(module.getId()).append("'");
                if (hasRejectAll) {
                    sb.append(" has been rejected:\n");
                } else {
                    sb.append(" that satisfies the version constraints: \n");
                }
            }

            for (String path : pathTo(incomingEdge)) {
                sb.append("   ").append(path);
                sb.append(" ").append(renderVersionConstraint(selector.getVersionConstraint()));
                renderReason(sb, selector);
                sb.append("\n");
            }
            first = false;
        }
        return sb.toString();
    }

    private Collection<EdgeState> getIncomingEdges(ModuleResolveState module) {
        Set<EdgeState> incoming = Sets.newLinkedHashSet();
        for (NodeState nodeState : module.getSelected().getNodes()) {
            incoming.addAll(nodeState.getIncomingEdges());
        }
        return incoming;
    }

    private static Collection<String> pathTo(EdgeState edge) {
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
            for (EdgeState e : path) {
                ModuleVersionIdentifier id = e.getFrom().getResolvedConfigurationId().getId();
                sb.append('\'').append(id).append('\'');
                sb.append(" --> ");
            }
            ModuleIdentifier moduleId = edge.getTargetComponent().getModule().getId();
            sb.append('\'').append(moduleId.getGroup()).append(':').append(moduleId.getName()).append('\'');
            result.add(sb.toString());
        }
        return result;
    }

    private static void renderReason(StringBuilder sb, SelectorState selector) {
        ComponentSelectionReasonInternal selectionReason = selector.getReasonForSelector();
        if (selectionReason.hasCustomDescriptions()) {
            sb.append(" because of the following reason");
            List<String> reasons = Lists.newArrayListWithExpectedSize(1);
            for (ComponentSelectionDescriptor componentSelectionDescriptor : selectionReason.getDescriptions()) {
                ComponentSelectionDescriptorInternal next = (ComponentSelectionDescriptorInternal) componentSelectionDescriptor;
                if (next.hasCustomDescription()) {
                    reasons.add(next.getDescription());
                }
            }
            if (reasons.size() == 1) {
                sb.append(": ").append(reasons.get(0));
            } else {
                sb.append("s: ");
                Joiner.on(", ").appendTo(sb, reasons);
            }
        }
    }

    private static void pathTo(EdgeState component, List<EdgeState> currentPath, List<List<EdgeState>> accumulator, Set<NodeState> alreadySeen) {
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

    private static String renderVersionConstraint(ResolvedVersionConstraint constraint) {
        if (isRejectAll(constraint)) {
            return "rejects all versions";
        }
        VersionSelector preferredSelector = constraint.getPreferredSelector();
        VersionSelector rejectedSelector = constraint.getRejectedSelector();
        StringBuilder sb = new StringBuilder("prefers ");
        sb.append('\'');
        sb.append(preferredSelector.getSelector());
        sb.append('\'');
        if (rejectedSelector != null) {
            sb.append(", rejects ");
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
        }
        return sb.toString();
    }

    private static boolean isRejectAll(ResolvedVersionConstraint constraint) {
        return "".equals(constraint.getPreferredVersion())
            && hasMatchAllSelector(constraint.getRejectedVersions());
    }

    private static boolean hasMatchAllSelector(List<String> rejectedVersions) {
        for (String version : rejectedVersions) {
            if ("+".equals(version)) {
                return true;
            }
        }
        return false;
    }
}
