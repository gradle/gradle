/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.DependenciesProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SecondLevelProblemGroup;
import org.gradle.api.problems.UndefinedProblemGroup;
import org.gradle.problems.internal.rendering.ProblemGroupRenderer;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Dependencies} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedDependenciesProblemGroup extends DependenciesProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Dependencies";

    private final DefaultSecondLevelProblemGroup declaration = new DefaultSecondLevelProblemGroup("Declaration", PredefinedProblemGroupDescriptions.DEPENDENCIES_DECLARATION, this);
    private final DefaultSecondLevelProblemGroup locking = new DefaultSecondLevelProblemGroup("Locking", PredefinedProblemGroupDescriptions.DEPENDENCIES_LOCKING, this);
    private final DefaultSecondLevelProblemGroup graphResolution = new DefaultSecondLevelProblemGroup("Graph Resolution", PredefinedProblemGroupDescriptions.DEPENDENCIES_GRAPH_RESOLUTION, this);
    private final DefaultSecondLevelProblemGroup artifactResolution = new DefaultSecondLevelProblemGroup("Artifact Resolution", PredefinedProblemGroupDescriptions.DEPENDENCIES_ARTIFACT_RESOLUTION, this);
    private final DefaultSecondLevelProblemGroup verification = new DefaultSecondLevelProblemGroup("Verification", PredefinedProblemGroupDescriptions.DEPENDENCIES_VERIFICATION, this);
    private final DefaultUndefinedProblemGroup undefined = new DefaultUndefinedProblemGroup(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(declaration, locking, graphResolution, artifactResolution, verification, undefined);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return PredefinedProblemGroupDescriptions.DEPENDENCIES;
    }

    @Override
    @Nullable
    public ProblemGroup getParent() {
        return null;
    }

    @Override
    @Nullable
    public ProblemGroupInternal getParentInternal() {
        return null;
    }

    @Override
    public SecondLevelProblemGroup getDeclaration() {
        return declaration;
    }

    @Override
    public SecondLevelProblemGroup getLocking() {
        return locking;
    }

    @Override
    public SecondLevelProblemGroup getGraphResolution() {
        return graphResolution;
    }

    @Override
    public SecondLevelProblemGroup getArtifactResolution() {
        return artifactResolution;
    }

    @Override
    public SecondLevelProblemGroup getVerification() {
        return verification;
    }

    @Override
    public UndefinedProblemGroup getUndefined() {
        return undefined;
    }

    @Override
    public SecondLevelProblemGroup group(String name) {
        return children.group(this, name);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) {
        return children.resolve(this, name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return ProblemGroupInternal.structurallyEquals(this, o);
    }

    @Override
    public int hashCode() {
        return ProblemGroupInternal.structuralHashCode(this);
    }

    @Override
    public String toString() {
        return ProblemGroupRenderer.render(this);
    }

    private Object writeReplace() {
        return SerializedProblemGroup.of(this);
    }
}
