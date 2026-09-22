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
import org.gradle.api.problems.ThirdLevelProblemGroup;
import org.gradle.problems.internal.rendering.ProblemGroupRenderer;
import org.jspecify.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.Serializable;

/**
 * A user-created group on the third level, below a {@link DefaultSecondLevelProblemGroup}. Never predefined, so it has no description.
 */
final class DefaultThirdLevelProblemGroup extends ThirdLevelProblemGroup implements ResolvableProblemGroup, Serializable {

    private final String name;
    private final ResolvableProblemGroup parent;

    DefaultThirdLevelProblemGroup(String name, ResolvableProblemGroup parent) {
        this.name = name;
        this.parent = parent;
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
        return null;
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
    public ProblemId problemId(String name) {
        String validated = ProblemNames.validateProblemName(name);
        return new DefaultProblemId(validated, validated, this);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) throws InvalidObjectException {
        throw new InvalidObjectException("Problem group '" + ProblemGroupRenderer.render(this) + "' cannot have sub-groups, but '" + name + "' was requested");
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
