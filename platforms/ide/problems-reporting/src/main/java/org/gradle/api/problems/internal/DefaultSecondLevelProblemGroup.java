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

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.SecondLevelProblemGroup;
import org.gradle.api.problems.ThirdLevelProblemGroup;
import org.gradle.api.problems.UndefinedProblemGroup;
import org.gradle.problems.internal.rendering.ProblemGroupRenderer;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A second-level group: either a predefined one (carrying a description) or one created by a producer through
 * {@code RootProblemGroup.group(String)}.
 */
final class DefaultSecondLevelProblemGroup extends SecondLevelProblemGroup implements ResolvableProblemGroup, Serializable {

    private final String name;
    @Nullable
    private final String description;
    private final ResolvableProblemGroup parent;
    /**
     * Only predefined sub-groups (the ones with a description) keep a canonical Undefined instance. User groups are created
     * afresh on every {@code group(name)} call, so their Undefined group is created on demand; equality is structural anyway.
     */
    @Nullable
    private final DefaultUndefinedProblemGroup undefined;

    DefaultSecondLevelProblemGroup(String name, @Nullable String description, ResolvableProblemGroup parent) {
        this.name = name;
        this.description = description;
        this.parent = parent;
        this.undefined = description == null ? null : new DefaultUndefinedProblemGroup(this, name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    @Nullable
    public String getDescription() {
        return description;
    }

    @Override
    public ProblemGroup getParent() {
        // every Gradle-owned implementation extends ProblemGroup
        return (ProblemGroup) parent;
    }

    @Override
    public ProblemGroupInternal getParentInternal() {
        return parent;
    }

    @Override
    public ThirdLevelProblemGroup group(String name) {
        String validated = ProblemNames.validateGroupName(name);
        if (validated.equalsIgnoreCase(ProblemNames.UNDEFINED_NAME)) {
            throw new IllegalArgumentException("'" + ProblemNames.UNDEFINED_NAME + "' is a reserved problem group name, use getUndefined() instead");
        }
        return new DefaultThirdLevelProblemGroup(validated, this);
    }

    @Override
    public ProblemId problemId(String name) {
        String validated = ProblemNames.validateProblemName(name);
        return new DefaultProblemId(validated, validated, this);
    }

    @Override
    public UndefinedProblemGroup getUndefined() {
        return undefined != null ? undefined : new DefaultUndefinedProblemGroup(this, name);
    }

    private DefaultUndefinedProblemGroup resolveUndefined() {
        return undefined != null ? undefined : new DefaultUndefinedProblemGroup(this, name);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) {
        return name.equals(ProblemNames.UNDEFINED_NAME) ? resolveUndefined() : new DefaultThirdLevelProblemGroup(name, this);
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
