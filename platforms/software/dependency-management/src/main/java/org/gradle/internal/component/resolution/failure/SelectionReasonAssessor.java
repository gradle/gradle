/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.component.resolution.failure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.MessageBuilderHelper;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A static utility class used by {@link ResolutionFailureHandler} to assess and classify
 * component selection failures during graph construction.
 * <p>
 * This type will map from types internal to the resolution engine to simple value types
 * defined as static inner types here that are serializable, lightweight, and contain only
 * the information necessary to describe the failure.
 */
public final class SelectionReasonAssessor {
    private SelectionReasonAssessor() { /* not instantiable */ }

    /**
     * Assess the reasons for selecting (or failing to select) a component of the given {@link ModuleResolveState}.
     *
     * @param moduleResolveState the module resolve state to assess
     * @return an {@link AssessedSelection} instance that summarizes the reasons for selecting (or failing to select) the module
     */
    public static AssessedSelection assessSelection(ModuleResolveState moduleResolveState) {
        Set<? extends DependencyGraphEdge> allIncomingEdges = moduleResolveState.getAllIncomingEdges();
        ImmutableList.Builder<AssessedSelection.AssessedSelectionReason> assessedReasons = ImmutableList.builderWithExpectedSize(allIncomingEdges.size());

        for (DependencyGraphEdge incomingEdge : allIncomingEdges) {
            String requestedVersion = getRequestedVersion(incomingEdge.getDependencyMetadata().getSelector());
            ImmutableList<ImmutableList<String>> pathNames = MessageBuilderHelper.findPathNamesTo(incomingEdge);
            ImmutableSet<ComponentSelectionCause> causes = getCauses(incomingEdge);

            assessedReasons.add(new AssessedSelection.AssessedSelectionReason(
                pathNames,
                requestedVersion,
                causes,
                incomingEdge.isFromLock()
            ));
        }

        return new AssessedSelection(moduleResolveState.getId(), assessedReasons.build());
    }

    private static ImmutableSet<ComponentSelectionCause> getCauses(DependencyGraphEdge incomingEdge) {
        ImmutableSet.Builder<ComponentSelectionCause> causes = ImmutableSet.builder();
        incomingEdge.visitSelectionReasons(reason -> causes.add(reason.getCause()));
        return causes.build();
    }

    private static @Nullable String getRequestedVersion(ComponentSelector selector) {
        VersionConstraint versionConstraint = getVersionConstraint(selector);
        if (versionConstraint != null) {
            return !versionConstraint.getStrictVersion().isEmpty()
                ? versionConstraint.getStrictVersion()
                : versionConstraint.getRequiredVersion();
        }
        return null;
    }

    private static @Nullable VersionConstraint getVersionConstraint(ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            return ((ModuleComponentSelector) selector).getVersionConstraint();
        } else {
            return null;
        }
    }

    /**
     * Simple serializable, lightweight value type that represents all the reasons for selecting a
     * specific module.
     */
    public static final class AssessedSelection {

        private final ModuleIdentifier moduleId;
        private final ImmutableList<AssessedSelectionReason> reasons;

        public AssessedSelection(ModuleIdentifier moduleId, ImmutableList<AssessedSelectionReason> reasons) {
            this.moduleId = moduleId;
            this.reasons = reasons;
        }

        public ModuleIdentifier getModuleId() {
            return moduleId;
        }

        public ImmutableList<AssessedSelectionReason> getReasons() {
            return reasons;
        }

        /**
         * Simple serializable, lightweight value type that represents a single reason for selecting a specific module,
         * including the version, the cause, and a description.
         */
        public static final class AssessedSelectionReason {

            private final ImmutableList<ImmutableList<String>> segmentedSelectionPaths;
            private final @Nullable String requestedVersion;
            private final ImmutableSet<ComponentSelectionCause> causes;
            private final boolean isFromLock;

            public AssessedSelectionReason(
                ImmutableList<ImmutableList<String>> segmentedSelectionPaths,
                @Nullable String requestedVersion,
                ImmutableSet<ComponentSelectionCause> causes,
                boolean isFromLock
            ) {
                this.segmentedSelectionPaths = segmentedSelectionPaths;
                this.requestedVersion = requestedVersion;
                this.causes = causes;
                this.isFromLock = isFromLock;
            }

            public ImmutableList<ImmutableList<String>> getSegmentedSelectionPaths() {
                return segmentedSelectionPaths;
            }

            public @Nullable String getRequestedVersion() {
                return requestedVersion;
            }

            public Set<ComponentSelectionCause> getCauses() {
                return causes;
            }

            public boolean isFromLock() {
                return isFromLock;
            }

        }

    }
}
