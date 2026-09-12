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
import org.gradle.api.problems.LeafProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SubProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Dependencies} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedDependenciesProblemGroup extends DependenciesProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Dependencies";

    private final DefaultSubProblemGroup declaration = new DefaultSubProblemGroup("Declaration", PredefinedProblemGroupDescriptions.DEPENDENCIES_DECLARATION, this);
    private final DefaultSubProblemGroup locking = new DefaultSubProblemGroup("Locking", PredefinedProblemGroupDescriptions.DEPENDENCIES_LOCKING, this);
    private final DefaultSubProblemGroup graphResolution = new DefaultSubProblemGroup("Graph Resolution", PredefinedProblemGroupDescriptions.DEPENDENCIES_GRAPH_RESOLUTION, this);
    private final DefaultSubProblemGroup artifactResolution = new DefaultSubProblemGroup("Artifact Resolution", PredefinedProblemGroupDescriptions.DEPENDENCIES_ARTIFACT_RESOLUTION, this);
    private final DefaultSubProblemGroup verification = new DefaultSubProblemGroup("Verification", PredefinedProblemGroupDescriptions.DEPENDENCIES_VERIFICATION, this);
    private final DefaultLeafProblemGroup undefined = DefaultLeafProblemGroup.undefined(this, NAME);
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
    public SubProblemGroup getDeclaration() {
        return declaration;
    }

    @Override
    public SubProblemGroup getLocking() {
        return locking;
    }

    @Override
    public SubProblemGroup getGraphResolution() {
        return graphResolution;
    }

    @Override
    public SubProblemGroup getArtifactResolution() {
        return artifactResolution;
    }

    @Override
    public SubProblemGroup getVerification() {
        return verification;
    }

    @Override
    public LeafProblemGroup getUndefined() {
        return undefined;
    }

    @Override
    public SubProblemGroup group(String name) {
        return children.group(this, name);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) {
        return children.resolve(this, name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return ProblemGroupSupport.equals(this, o);
    }

    @Override
    public int hashCode() {
        return ProblemGroupSupport.hashCode(this);
    }

    @Override
    public String toString() {
        return ProblemGroupSupport.render(this);
    }

    private Object writeReplace() {
        return SerializedProblemGroup.of(this);
    }
}
