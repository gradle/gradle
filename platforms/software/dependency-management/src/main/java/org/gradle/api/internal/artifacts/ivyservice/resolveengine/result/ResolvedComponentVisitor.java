/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface ResolvedComponentVisitor {
    /**
     * Starts visiting a component.
     */
    void startVisitComponent(Long id, ComponentSelectionReason selectionReason, @Nullable String repoName);

    /**
     * Visit graph independent details of the component.
     */
    void visitComponentDetails(ComponentIdentifier componentId, ModuleVersionIdentifier moduleVersion);

    /**
     * Visit a selected variant of the component.
     */
    void visitSelectedVariant(Long id, ResolvedVariantResult variant);

    /**
     * Visit variants of the component.
     */
    void visitComponentVariants(List<ResolvedVariantResult> allVariants);

    /**
     * Finishes visiting a component.
     */
    void endVisitComponent();
}
