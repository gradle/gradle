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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.CompositeVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.InverseVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;

import java.util.List;
import java.util.Set;

public class RejectedModuleMessageBuilder {
    public String buildFailureMessage(ModuleResolveState module) {
        boolean hasRejectAll = false;
        for (SelectorState candidate : module.getSelectors()) {
            hasRejectAll |= candidate.getVersionConstraint().isRejectAll();
        }
        StringBuilder sb = new StringBuilder();
        if (hasRejectAll) {
            sb.append("Module '").append(module.getId()).append("' has been rejected:\n");
        } else {
            sb.append("Cannot find a version of '").append(module.getId()).append("' that satisfies the version constraints: \n");
        }

        Set<EdgeState> allEdges = Sets.newLinkedHashSet();
        allEdges.addAll(module.getIncomingEdges());
        allEdges.addAll(module.getUnattachedDependencies());
        renderEdges(sb, allEdges);
        return sb.toString();
    }

    private void renderEdges(StringBuilder sb, Set<EdgeState> incomingEdges) {
        for (EdgeState incomingEdge : incomingEdges) {
            SelectorState selector = incomingEdge.getSelector();
            for (String path : MessageBuilderHelper.pathTo(incomingEdge, false)) {
                sb.append("   ").append(path);
                sb.append(" --> ");
                renderSelector(sb, selector);
                renderReason(sb, selector);
                sb.append("\n");
            }
        }
    }

    static void renderSelector(StringBuilder sb, SelectorState selectorState) {
        ResolvedVersionConstraint constraint = selectorState.getVersionConstraint();
        ModuleIdentifier moduleId = selectorState.getTargetModule().getId();

        sb.append('\'').append(moduleId.getGroup()).append(':').append(moduleId.getName());

        if (constraint.isRejectAll()) {
            sb.append("' rejects all versions");
            return;
        }

        VersionSelector requiredSelector = constraint.getRequiredSelector();
        VersionSelector rejectedSelector = constraint.getRejectedSelector();

        if (rejectedSelector instanceof InverseVersionSelector) {
            sb.append("' strictly '").append(requiredSelector.getSelector()).append("'");
            return;
        }

        if (requiredSelector != null) {
            sb.append(':').append(requiredSelector.getSelector());
        }
        sb.append("'");

        if (constraint.getPreferredSelector() != null) {
            sb.append(" prefers '").append(constraint.getPreferredSelector().getSelector()).append("'");
        }

        if (rejectedSelector == null) {
            return;
        }

        sb.append(" rejects ");
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

    private static void renderReason(StringBuilder sb, SelectorState selector) {
        ComponentSelectionReasonInternal selectionReason = selector.getSelectionReason();
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

}
