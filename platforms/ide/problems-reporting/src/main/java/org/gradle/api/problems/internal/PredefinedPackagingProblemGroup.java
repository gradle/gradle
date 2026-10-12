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

import org.gradle.api.problems.PackagingProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SecondLevelProblemGroup;
import org.gradle.api.problems.UndefinedProblemGroup;
import org.gradle.problems.internal.rendering.ProblemGroupRenderer;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Packaging} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedPackagingProblemGroup extends PackagingProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Packaging";

    private final DefaultSecondLevelProblemGroup distributions = new DefaultSecondLevelProblemGroup("Distributions", PredefinedProblemGroupDescriptions.PACKAGING_DISTRIBUTIONS, this);
    private final DefaultSecondLevelProblemGroup jar = new DefaultSecondLevelProblemGroup("JAR", PredefinedProblemGroupDescriptions.PACKAGING_JAR, this);
    private final DefaultSecondLevelProblemGroup nativeLinking = new DefaultSecondLevelProblemGroup("Native Linking", PredefinedProblemGroupDescriptions.PACKAGING_NATIVE_LINKING, this);
    private final DefaultSecondLevelProblemGroup signing = new DefaultSecondLevelProblemGroup("Signing", PredefinedProblemGroupDescriptions.PACKAGING_SIGNING, this);
    private final DefaultUndefinedProblemGroup undefined = new DefaultUndefinedProblemGroup(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(distributions, jar, nativeLinking, signing, undefined);

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
        return PredefinedProblemGroupDescriptions.PACKAGING;
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
    public SecondLevelProblemGroup getDistributions() {
        return distributions;
    }

    @Override
    public SecondLevelProblemGroup getJar() {
        return jar;
    }

    @Override
    public SecondLevelProblemGroup getNativeLinking() {
        return nativeLinking;
    }

    @Override
    public SecondLevelProblemGroup getSigning() {
        return signing;
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
