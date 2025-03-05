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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleSelectors;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.SelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.exception.ComponentSelectionException;
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure;

import javax.inject.Inject;

public abstract class ModuleRejectedIncompatibleRequiredVersionsFailureDescriber extends AbstractResolutionFailureDescriber<ModuleRejectedFailure> {
    @Inject
    @Override
    protected abstract DocumentationRegistry getDocumentationRegistry();

    @Override
    public boolean canDescribeFailure(ModuleRejectedFailure failure) {
        return failure.getModuleResolveState().getSelectionReasons().stream().anyMatch(ModuleRejectedIncompatibleRequiredVersionsFailureDescriber::isConflictingReason);
    }

    @Override
    public AbstractResolutionFailureException describeFailure(ModuleRejectedFailure failure) {
        return new ComponentSelectionException(summarizeSelectionRequirements(failure), failure);
    }

    private String summarizeSelectionRequirements(ModuleRejectedFailure failure) {
        StringBuilder sb = new StringBuilder("There were conflicting requirements:\n");
        ModuleSelectors<SelectorState> selectors = failure.getModuleResolveState().getSelectors();
        selectors.forEach(selector -> {
            ComponentSelectionReasonInternal reason = selector.getSelectionReason();
            if (isConflictingReason(reason)) {
                sb.append(selector.getSelectionReason()).append(" ").append(selector.getSelector().getDisplayName()).append("\n");
            }
        });
        return sb.toString();
    }

    private static boolean isConflictingReason(ComponentSelectionReasonInternal reason) {
        return reason.isForced() || reason.isConstrained();
    }
}
