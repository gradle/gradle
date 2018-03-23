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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CompositeVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentStateWithDependents;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RejectedComponentMessageBuilder implements ModuleResolutionMessageBuilder {
    @Override
    public String buildFailureMessage(Collection<? extends ComponentResolutionState> candidates) {
        boolean hasRejectAll = false;
        for (ComponentResolutionState candidate : candidates) {
            hasRejectAll |= isRejectAll(candidate.getVersionConstraint());
        }
        StringBuilder sb = new StringBuilder();
        if (hasRejectAll) {
            sb.append("Module ");
        } else {
            sb.append("Cannot find a version of ");
        }
        boolean first = true;
        for (ComponentResolutionState candidate : candidates) {
            if (first) {
                sb.append("'").append(candidate.getId().getModule()).append("'");
                if (hasRejectAll) {
                    sb.append(" has been rejected:\n");
                } else {
                    sb.append(" that satisfies the version constraints: \n");
                }
            }
            if (candidate instanceof ComponentStateWithDependents) {
                ComponentStateWithDependents component = (ComponentStateWithDependents) candidate;
                List<String> paths = pathTo(component);
                for (String path : paths) {
                    sb.append("   ").append(path).append("\n");
                }
            } else {
                sb.append(renderVersionConstraint(candidate.getVersionConstraint()));
            }
            first = false;
        }
        return sb.toString();
    }

    private static List<String> pathTo(ComponentStateWithDependents component) {
        List<List<ComponentStateWithDependents>> acc = Lists.newArrayListWithExpectedSize(1);
        pathTo(component, Lists.<ComponentStateWithDependents>newArrayList(), acc, Sets.<ComponentStateWithDependents>newHashSet());
        List<String> result = Lists.newArrayListWithCapacity(acc.size());
        for (List<ComponentStateWithDependents> path : acc) {
            ComponentStateWithDependents target = Iterators.getLast(path.iterator());
            StringBuilder sb = new StringBuilder();
            if (target.isFromPendingNode()) {
                sb.append("Constraint path ");
            } else {
                sb.append("Dependency path ");
            }
            for (Iterator<ComponentStateWithDependents> iterator = path.iterator(); iterator.hasNext();) {
                ComponentStateWithDependents e = iterator.next();
                ModuleVersionIdentifier id = e.getId();
                if (iterator.hasNext()) {
                    sb.append('\'').append(id).append('\'');
                    sb.append(" --> ");
                } else {
                    sb.append('\'').append(id.getGroup()).append(':').append(id.getName()).append('\'');
                    sb.append(" ").append(renderVersionConstraint(e.getVersionConstraint()));
                    renderReason(sb, e);
                }
            }
            result.add(sb.toString());
        }
        return result;
    }

    private static void renderReason(StringBuilder sb, ComponentStateWithDependents e) {
        ComponentSelectionReasonInternal selectionReason = e.getSelectionReason();
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

    private static void pathTo(ComponentStateWithDependents component, List<ComponentStateWithDependents> currentPath, List<List<ComponentStateWithDependents>> accumulator, Set<ComponentStateWithDependents> alreadySeen) {
        if (alreadySeen.add(component)) {
            currentPath.add(0, component);
            Collection<ComponentState> dependents = component.getDependents();
            List<ComponentState> unattachedDependencies = component.getUnattachedDependencies();
            Set<ComponentState> allDependents = Sets.newLinkedHashSet();
            allDependents.addAll(dependents);
            allDependents.addAll(unattachedDependencies);
            for (ComponentStateWithDependents dependent : allDependents) {
                List<ComponentStateWithDependents> otherPath = Lists.newArrayList(currentPath);
                pathTo(dependent, otherPath, accumulator, alreadySeen);
            }
            if (allDependents.isEmpty()) {
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
