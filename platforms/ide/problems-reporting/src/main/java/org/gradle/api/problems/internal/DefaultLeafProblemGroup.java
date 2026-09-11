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

import org.gradle.api.problems.LeafProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.jspecify.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.Serializable;

/**
 * A terminal group: a third-level group created by a producer, a synthetic {@code Undefined} group, or a sub-group of the
 * closed {@code Gradle} root group.
 */
final class DefaultLeafProblemGroup extends LeafProblemGroup implements ResolvableProblemGroup, Serializable {

    private final String name;
    @Nullable
    private final String description;
    private final ProblemGroup parent;

    DefaultLeafProblemGroup(String name, @Nullable String description, ProblemGroup parent) {
        this.name = name;
        this.description = description;
        this.parent = parent;
    }

    /**
     * The synthetic {@code Undefined} child of {@code parent}. The parent's name is passed explicitly because the parent may
     * still be under construction.
     */
    static DefaultLeafProblemGroup undefined(ProblemGroup parent, String parentName) {
        return new DefaultLeafProblemGroup(ProblemGroupSupport.UNDEFINED_NAME, PredefinedProblemGroupDescriptions.undefined(parentName), parent);
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
        return parent;
    }

    @Override
    public ProblemId problem(String name) {
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
