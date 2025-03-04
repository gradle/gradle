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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.SelectorState;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A static utility class used by {@link ResolutionFailureHandler} to assess and classify
 * component selection failures during graph construction.
 * <p>
 * This type will map from types internal to the resolution engine to simple value types
 * defined as static inner types here that are serializable, lightweight, and contain only
 * the information necessary to describe the failure.
 */
public final class SelectionReasonAssessor {
    public static AssessedSelection assessSelectionReason(ModuleResolveState moduleResolveState) {
        List<AssessedSelection.AssessedSelectionReason> paths = streamSelectors(moduleResolveState)
            .flatMap(selectorState -> assessSelectorState(selectorState).stream())
            .collect(Collectors.toList());

        return new AssessedSelection(moduleResolveState.getId(), paths, Objects.requireNonNull(moduleResolveState.getSelected()).getRejectedErrorMessage());
    }

    @Nonnull
    private static Stream<SelectorState> streamSelectors(ModuleResolveState moduleResolveState) {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(moduleResolveState.getSelectors().iterator(), Spliterator.ORDERED),
            false
        );
    }

    private static List<AssessedSelection.AssessedSelectionReason> assessSelectorState(SelectorState selectorState) {
        String version = describeSelectedVersion(selectorState);
        boolean isStrictRequirement = isStrict(selectorState);

        return selectorState.getSelectionReason().getDescriptions().stream()
            .map(selectionDescriptor -> new AssessedSelection.AssessedSelectionReason(
                version,
                isStrictRequirement,
                selectionDescriptor.getCause(),
                selectionDescriptor.getDescription()
            )).collect(Collectors.toList());
    }

    private static String describeSelectedVersion(SelectorState selectorState) {
        if (selectorState.getVersionConstraint() == null) {
            return "unspecified";
        } else {
            return selectorState.getVersionConstraint().getSelectors().stream()
                .map(VersionSelector::getSelector)
                .collect(Collectors.joining());
        }
    }

    private static boolean isStrict(SelectorState selectorState) {
        ResolvedVersionConstraint constraint = selectorState.getVersionConstraint();
        return constraint != null && constraint.isStrict();
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
            private final String version;
            private final boolean exactVersionNeeded;
            private final ComponentSelectionCause cause;
            private final String description;

            public AssessedSelectionReason(String version, boolean exactVersionNeeded, ComponentSelectionCause cause, String description) {
                this.version = version;
                this.exactVersionNeeded = exactVersionNeeded;
                this.cause = cause;
                this.description = description;
            }

            public String getVersion() {
                return version;
            }

            public boolean isExactVersionNeeded() {
                return exactVersionNeeded;
            }

            public ComponentSelectionCause getCause() {
                return cause;
            }

            public String getDescription() {
                return description;
            }
        }
    }
}
