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
import org.gradle.api.problems.UndefinedProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.Serializable;

/**
 * The synthetic {@code Undefined} group below a root group, the Gradle root group, or a sub-group.
 */
final class DefaultUndefinedProblemGroup extends UndefinedProblemGroup implements ResolvableProblemGroup, Serializable {

    private final String description;
    private final ResolvableProblemGroup parent;

    /**
     * The synthetic {@code Undefined} child of {@code parent}. The parent's name is passed explicitly because the parent may
     * still be under construction.
     */
    DefaultUndefinedProblemGroup(ResolvableProblemGroup parent, String parentName) {
        this.description = PredefinedProblemGroupDescriptions.undefined(parentName);
        this.parent = parent;
    }

    @Override
    public String getName() {
        return ProblemGroupSupport.UNDEFINED_NAME;
    }

    @Override
    public String getDisplayName() {
        return ProblemGroupSupport.UNDEFINED_NAME;
    }

    @Override
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
    public ProblemId problemId(String name) {
        String validated = ProblemGroupSupport.validateProblemName(name);
        return new DefaultProblemId(validated, validated, this);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) throws InvalidObjectException {
        throw new InvalidObjectException("Problem group '" + ProblemGroupSupport.render(this) + "' cannot have sub-groups, but '" + name + "' was requested");
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
