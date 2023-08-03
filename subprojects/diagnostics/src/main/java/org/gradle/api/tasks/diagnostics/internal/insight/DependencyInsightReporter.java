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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DefaultSection;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyReportHeader;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RequestedVersion;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.ResolvedDependencyEdge;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.Section;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvedDependencyEdge;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DependencyInsightReporter {

    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;

    private static final InternalTransformer<DependencyEdge, DependencyResult> TO_EDGES = result -> {
        if (result instanceof UnresolvedDependencyResult) {
            return new UnresolvedDependencyEdge((UnresolvedDependencyResult) result);
        } else {
            return new ResolvedDependencyEdge((ResolvedDependencyResult) result);
        }
    };

    public DependencyInsightReporter(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
    }

    public Collection<RenderableDependency> convertToRenderableItems(Collection<DependencyResult> dependencies, boolean singlePathToDependency) {
        LinkedList<RenderableDependency> out = new LinkedList<>();
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
        ComponentSelectionReasonInternal reason = (ComponentSelectionReasonInternal) dependency.getReason();
        Section selectionReasonsSection = buildSelectionReasonSection(reason);
        List<Section> reasonSections = selectionReasonsSection.getChildren();

        String reasonShortDescription;
        List<Section> extraDetails = Lists.newArrayList();

        boolean displayFullReasonSection = reason.hasCustomDescriptions() || reasonSections.size() > 1;
        if (displayFullReasonSection) {
            reasonShortDescription = null;
            extraDetails.add(selectionReasonsSection);
        } else {
            reasonShortDescription = reasonSections.isEmpty() ? null : reasonSections.get(0).getDescription().toLowerCase();
        }

        buildFailureSection(dependency, alreadyReportedErrors, extraDetails);
        return new DependencyReportHeader(dependency, reasonShortDescription, extraDetails);
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

    private static void buildFailureSection(DependencyEdge edge, Set<Throwable> alreadyReportedErrors, List<Section> sections) {
        if (edge instanceof UnresolvedDependencyEdge) {
            UnresolvedDependencyEdge unresolved = (UnresolvedDependencyEdge) edge;
            Throwable failure = unresolved.getFailure();
            DefaultSection failures = new DefaultSection("Failures");
            String errorMessage = collectErrorMessages(failure, alreadyReportedErrors);
            failures.addChild(new DefaultSection(errorMessage));
            sections.add(failures);
        }
    }

    private static String collectErrorMessages(Throwable failure, Set<Throwable> alreadyReportedErrors) {
        TreeFormatter formatter = new TreeFormatter();
        collectErrorMessages(failure, formatter, alreadyReportedErrors);
        return formatter.toString();
    }

    private static void collectErrorMessages(Throwable failure, TreeFormatter formatter, Set<Throwable> alreadyReportedErrors) {
        if (alreadyReportedErrors.add(failure)) {
            formatter.node(failure.getMessage());
            if(failure instanceof ResolutionProvider){
                ((ResolutionProvider) failure).getResolutions()
                    .forEach(formatter::node);
            }
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

    private static DefaultSection buildSelectionReasonSection(ComponentSelectionReason reason) {
        DefaultSection selectionReasons = new DefaultSection("Selection reasons");
        for (ComponentSelectionDescriptor entry : reason.getDescriptions()) {
            ComponentSelectionDescriptorInternal descriptor = (ComponentSelectionDescriptorInternal) entry;
            boolean hasCustomDescription = descriptor.hasCustomDescription();

            if (ComponentSelectionReasons.isCauseExpected(descriptor) && !hasCustomDescription) {
                // Don't render empty 'requested' reason
                continue;
            }

            Section item = new DefaultSection(render(descriptor));
            selectionReasons.addChild(item);
        }
        return selectionReasons;
    }

    private static String render(ComponentSelectionDescriptor descriptor) {
        if (((ComponentSelectionDescriptorInternal) descriptor).hasCustomDescription()) {
            return prettyCause(descriptor.getCause()) + ": " + descriptor.getDescription();
        }
        return prettyCause(descriptor.getCause());
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
            case BY_ANCESTOR:
                return "By ancestor";
            default:
                assert false : "Missing an enum value " + cause;
                return cause.getDefaultReason();
        }
    }
}
