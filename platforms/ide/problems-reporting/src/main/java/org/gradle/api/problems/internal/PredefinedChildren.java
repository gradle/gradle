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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.problems.SecondLevelProblemGroup;

import java.io.InvalidObjectException;
import java.util.Arrays;

/**
 * The predefined children of a root group, and the lookup-or-create logic shared by all root groups.
 */
final class PredefinedChildren {

    private final ImmutableMap<String, ResolvableProblemGroup> byName;

    PredefinedChildren(ResolvableProblemGroup... children) {
        this.byName = Maps.uniqueIndex(Arrays.asList(children), ResolvableProblemGroup::getName);
    }

    /**
     * The predefined children in declaration order, the order the documentation lists them in.
     */
    ImmutableList<ResolvableProblemGroup> all() {
        return byName.values().asList();
    }

    /**
     * Implements {@code RootProblemGroup.group(String)}: predefined sibling on exact match, otherwise a new user group.
     */
    SecondLevelProblemGroup group(ResolvableProblemGroup owner, String name) {
        String validated = ProblemNames.validateGroupName(name);
        if (validated.equalsIgnoreCase(ProblemNames.UNDEFINED_NAME)) {
            throw new IllegalArgumentException("'" + ProblemNames.UNDEFINED_NAME + "' is a reserved problem group name, use getUndefined() instead");
        }
        ResolvableProblemGroup exact = byName.get(validated);
        if (exact != null) {
            // open roots only have DefaultSecondLevelProblemGroup children besides Undefined, which is rejected above
            return (SecondLevelProblemGroup) exact;
        }
        // Names are case-sensitive, so "java" is a distinct user group next to the predefined "Java". Rejecting it would
        // break a plugin as soon as Gradle adds a predefined group whose name only differs by case from the plugin's.
        // The reserved name above is the one exception: Undefined exists at every level from day one, so no plugin can
        // have a working group of that name that a later Gradle version would break, and the spec forbids creating one.
        return new DefaultSecondLevelProblemGroup(validated, null, owner);
    }

    /**
     * Resolves a child of an open root group when reading a serialized path.
     */
    ResolvableProblemGroup resolve(ResolvableProblemGroup owner, String name) {
        ResolvableProblemGroup exact = byName.get(name);
        return exact != null ? exact : new DefaultSecondLevelProblemGroup(name, null, owner);
    }

    /**
     * Resolves a child of a closed root group when reading a serialized path: only predefined children exist.
     */
    ResolvableProblemGroup resolveClosed(String name) throws InvalidObjectException {
        ResolvableProblemGroup exact = byName.get(name);
        if (exact == null) {
            throw new InvalidObjectException("Unknown predefined problem group '" + name + "'");
        }
        return exact;
    }
}
