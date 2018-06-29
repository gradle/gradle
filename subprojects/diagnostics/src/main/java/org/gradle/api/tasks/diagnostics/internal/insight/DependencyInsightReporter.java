/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.insight;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DefaultSection;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyReportHeader;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RequestedVersion;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.ResolvedDependencyEdge;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.Section;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvedDependencyEdge;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DependencyInsightReporter {

    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;

    private static final Transformer<DependencyEdge, DependencyResult> TO_EDGES = new Transformer<DependencyEdge, DependencyResult>() {
        @Override
        public DependencyEdge transform(DependencyResult result) {
            if (result instanceof UnresolvedDependencyResult) {
                return new UnresolvedDependencyEdge((UnresolvedDependencyResult) result);
            } else {
                return new ResolvedDependencyEdge((ResolvedDependencyResult) result);
            }
        }
    };

    public DependencyInsightReporter(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
    }

    public Collection<RenderableDependency> convertToRenderableItems(Collection<DependencyResult> dependencies, boolean singlePathToDependency) {
        LinkedList<RenderableDependency> out = new LinkedList<RenderableDependency>();
        Collection<DependencyEdge> sortedEdges = toDependencyEdges(dependencies);

        //remember if module id was annotated
        Set<ComponentIdentifier> annotated = Sets.newHashSet();
        Set<Throwable> alreadyReportedErrors = Sets.newHashSet();
        RequestedVersion current = null;
        for (DependencyEdge dependency : sortedEdges) {
            //add description only to the first module
            if (annotated.add(dependency.getActual())) {
                DependencyReportHeader header = createHeaderForDependency(dependency, alreadyReportedErrors);
                out.add(header);
                current = newRequestedVersion(out, dependency);
            } else if (!current.getRequested().equals(dependency.getRequested())) {
                current = newRequestedVersion(out, dependency);
            }
            if (!singlePathToDependency || current.getChildren().isEmpty()) {
                current.addChild(dependency);
            }
        }

        return out;
    }

    private DependencyReportHeader createHeaderForDependency(DependencyEdge dependency, Set<Throwable> alreadyReportedErrors) {
        ComponentSelectionReason reason = dependency.getReason();
        String reasonDescription = getReasonDescription(reason);
        SelectionReasonsSection selectionReasonsSection = buildSelectionReasonSection(reason);
        if (selectionReasonsSection.replacesShortDescription) {
            reasonDescription = null;
        }
        List<Section> extraDetails = buildExtraDetails(!selectionReasonsSection.replacesShortDescription ? null : selectionReasonsSection, buildFailureSection(dependency, alreadyReportedErrors));
        ResolvedVariantResult selectedVariant = dependency.getSelectedVariant();
        return new DependencyReportHeader(dependency, reasonDescription, selectedVariant, extraDetails);
    }

    private RequestedVersion newRequestedVersion(LinkedList<RenderableDependency> out, DependencyEdge dependency) {
        RequestedVersion current;
        current = new RequestedVersion(dependency.getRequested(), dependency.getActual(), dependency.isResolvable());
        out.add(current);
        return current;
    }

    private Collection<DependencyEdge> toDependencyEdges(Collection<DependencyResult> dependencies) {
        List<DependencyEdge> edges = CollectionUtils.collect(dependencies, TO_EDGES);
        return DependencyResultSorter.sort(edges, versionSelectorScheme, versionComparator, versionParser);
    }

    private static Section buildFailureSection(DependencyEdge edge, Set<Throwable> alreadyReportedErrors) {
        if (edge instanceof UnresolvedDependencyEdge) {
            UnresolvedDependencyEdge unresolved = (UnresolvedDependencyEdge) edge;
            Throwable failure = unresolved.getFailure();
            if (failure != null) {
                DefaultSection failures = new DefaultSection("Failures");
                String errorMessage = collectErrorMessages(failure, alreadyReportedErrors);
                failures.addChild(new DefaultSection(errorMessage));
                return failures;
            }
        }
        return null;
    }

    private static String collectErrorMessages(Throwable failure, Set<Throwable> alreadyReportedErrors) {
        TreeFormatter formatter = new TreeFormatter(false);
        collectErrorMessages(failure, formatter, alreadyReportedErrors);
        return formatter.toString();
    }

    private static void collectErrorMessages(Throwable failure, TreeFormatter formatter, Set<Throwable> alreadyReportedErrors) {
        if (alreadyReportedErrors.add(failure)) {
            formatter.node(failure.getMessage());
            Throwable cause = failure.getCause();
            if (alreadyReportedErrors.contains(cause)) {
                formatter.append(" (already reported)");
            }
            if (cause != null && cause != failure) {
                formatter.startChildren();
                collectErrorMessages(cause, formatter, alreadyReportedErrors);
                formatter.endChildren();
            }
        }
    }

    private static List<Section> buildExtraDetails(Section... sections) {
        ImmutableList.Builder<Section> builder = new ImmutableList.Builder<Section>();
        for (Section section : sections) {
            if (section != null) {
                builder.add(section);
            }
        }
        return builder.build();
    }

    private static SelectionReasonsSection buildSelectionReasonSection(ComponentSelectionReason reason) {
        SelectionReasonsSection selectionReasons = new SelectionReasonsSection();
        for (ComponentSelectionDescriptor entry : reason.getDescriptions()) {
            ComponentSelectionDescriptorInternal descriptor = (ComponentSelectionDescriptorInternal) entry;
            ComponentSelectionCause cause = descriptor.getCause();
            boolean hasCustomDescription = descriptor.hasCustomDescription();
            String message = null;
            if (hasCustomDescription) {
                selectionReasons.shouldDisplay();
                message = descriptor.getDescription();
            }
            String prettyCause = prettyCause(cause);
            Section item = new DefaultSection(hasCustomDescription ? prettyCause + " : " + message : prettyCause);
            selectionReasons.addChild(item);
        }
        return selectionReasons;
    }

    private static String prettyCause(ComponentSelectionCause cause) {
        switch (cause) {
            case ROOT:
                return "Root component";
            case REQUESTED:
                return "Was requested";
            case SELECTED_BY_RULE:
                return "Selected by rule";
            case FORCED:
                return "Forced";
            case CONFLICT_RESOLUTION:
                return "By conflict resolution";
            case COMPOSITE_BUILD:
                return "By composite build";
            case REJECTION:
                return "Rejection";
            case CONSTRAINT:
                return "By constraint";
        }
        return "Unknown";
    }

    private static String getReasonDescription(ComponentSelectionReason reason) {
        ComponentSelectionReasonInternal r = (ComponentSelectionReasonInternal) reason;
        String description = getReasonDescription(r);
        if (reason.isConstrained()) {
            if (!r.hasCustomDescriptions()) {
                return "via constraint";
            } else {
                return "via constraint, " + description;
            }
        }
        return description;
    }

    private static String getReasonDescription(ComponentSelectionReasonInternal reason) {
        if (!reason.hasCustomDescriptions()) {
            return reason.isExpected() ? null : Iterables.getLast(reason.getDescriptions()).getDescription();
        }
        return getLastCustomReason(reason);
    }

    private static String getLastCustomReason(ComponentSelectionReasonInternal reason) {
        String lastCustomReason = null;
        for (ComponentSelectionDescriptor descriptor : reason.getDescriptions()) {
            if (((ComponentSelectionDescriptorInternal) descriptor).hasCustomDescription()) {
                lastCustomReason = descriptor.getDescription();
            }
        }
        return lastCustomReason;
    }

    private static class SelectionReasonsSection extends DefaultSection {

        private boolean replacesShortDescription;

        public SelectionReasonsSection() {
            super("Selection reasons");
        }

        public void shouldDisplay() {
            replacesShortDescription = true;
        }
    }

}
