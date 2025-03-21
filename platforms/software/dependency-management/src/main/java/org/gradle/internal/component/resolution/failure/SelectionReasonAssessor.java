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
import org.apache.commons.lang.WordUtils;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.SelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
     * Assess the reasons for selecting (or failing to select) a module, based on the given {@link ModuleResolveState}.
     *
     * @param moduleResolveState the module resolve state to assess
     * @return an {@link AssessedSelection} instance that summarizes the reasons for selecting (or failing to select) the module
     */
    @SuppressWarnings("CodeBlock2Expr")
    public static AssessedSelection assessSelection(ModuleResolveState moduleResolveState) {
        Map<SelectorState, List<List<String>>> pathsBySelectors = moduleResolveState.getSegmentedPathsBySelectors();

        List<AssessedSelection.AssessedSelectionReason> assessedReasons = new ArrayList<>(pathsBySelectors.size());
        pathsBySelectors.forEach((selector, paths) -> {
            paths.forEach(path -> {
                assessedReasons.addAll(assessReason(selector, path));
            });
        });

        return new AssessedSelection(moduleResolveState.getId(), assessedReasons, Objects.requireNonNull(moduleResolveState.getSelected()).getRejectedErrorMessage());
    }

    private static List<AssessedSelection.AssessedSelectionReason> assessReason(SelectorState selectorState, List<String> pathSegments) {
        boolean isStrictRequirement = isStrictRequirement(selectorState);
        String requiredVersion = describeRequiredVersion(selectorState);

        return selectorState.getSelectionReason().getDescriptions().stream()
            .map(selectionDescriptor -> new AssessedSelection.AssessedSelectionReason(
                selectorState.getRequested(),
                selectorState.getSelector(),
                pathSegments,
                requiredVersion,
                isStrictRequirement,
                selectionDescriptor.getCause(),
                describeSelectionReason(selectionDescriptor),
                selectorState.isFromLock()
            )).collect(Collectors.toList());
    }

    private static boolean isStrictRequirement(SelectorState selectorState) {
        ResolvedVersionConstraint constraint = selectorState.getVersionConstraint();
        return constraint != null && constraint.isStrict();
    }

    private static String describeRequiredVersion(SelectorState selectorState) {
        ResolvedVersionConstraint versionConstraint = selectorState.getVersionConstraint();
        if (versionConstraint == null) {
            return "unspecified";
        } else {
            VersionSelector requiredSelector = versionConstraint.getRequiredSelector();
            return requiredSelector != null ? requiredSelector.getSelector() : "unspecified";
        }
    }

    private static String describeSelectionReason(ComponentSelectionDescriptorInternal selectionDescriptor) {
        if (selectionDescriptor.hasCustomDescription()) {
            return selectionDescriptor.getDescription();
        } else {
            return WordUtils.capitalize(selectionDescriptor.getDescription());
        }
    }

    /**
     * Simple serializable, lightweight value type that represents all the reasons for selecting a
     * specific module.
     */
    public static final class AssessedSelection {
        private final ModuleIdentifier moduleId;
        private final ImmutableList<AssessedSelectionReason> reasons;
        private final String legacyErrorMsg;

        public AssessedSelection(ModuleIdentifier moduleId, List<AssessedSelectionReason> reasons, String legacyErrorMsg) {
            this.moduleId = moduleId;
            this.reasons = ImmutableList.copyOf(reasons);
            this.legacyErrorMsg = legacyErrorMsg;
        }

        public ModuleIdentifier getModuleId() {
            return moduleId;
        }

        public List<AssessedSelectionReason> getReasons() {
            return reasons;
        }

        public String getLegacyErrorMsg() {
            return legacyErrorMsg;
        }

        /**
         * Simple serializable, lightweight value type that represents a single reason for selecting a specific module,
         * including the version, the cause, and a description.
         */
        public static final class AssessedSelectionReason {
            private final ComponentSelector requested;
            private final ComponentSelector selected;
            private final List<String> segmentedSelectionPath;
            private final String requiredVersion;
            private final boolean isStrict;
            private final ComponentSelectionCause cause;
            private final String description;
            private final boolean isFromLock;

            public AssessedSelectionReason(ComponentSelector requested, ComponentSelector selected, List<String> segmentedSelectionPath, String requiredVersion, boolean isStrict, ComponentSelectionCause cause, String description, boolean isFromLock) {
                this.requested = requested;
                this.selected = selected;
                this.segmentedSelectionPath = ImmutableList.copyOf(segmentedSelectionPath);
                this.requiredVersion = requiredVersion;
                this.isStrict = isStrict;
                this.cause = cause;
                this.description = description;
                this.isFromLock = isFromLock;
            }

            public ComponentSelector getRequested() {
                return requested;
            }

            public ComponentSelector getSelected() {
                return selected;
            }

            public List<String> getSegmentedSelectionPath() {
                return segmentedSelectionPath;
            }

            public String getRequiredVersion() {
                return requiredVersion;
            }

            public boolean isStrict() {
                return isStrict;
            }

            public ComponentSelectionCause getCause() {
                return cause;
            }

            public String getDescription() {
                return description;
            }

            public boolean isFromLock() {
                return isFromLock;
            }
        }
    }
}
