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

import org.gradle.api.problems.ProblemId;

import java.io.InvalidObjectException;

/**
 * A Gradle-owned group that can resolve a child by name when a serialized group path is read back. Every group in the
 * predefined hierarchy, including user-created descendants, implements this, so a path can be walked without casts.
 */
interface ResolvableProblemGroup extends ProblemGroupInternal {

    /**
     * Returns the predefined child with the given name, or creates the user-defined child, or fails if this group cannot have
     * such a child.
     */
    ResolvableProblemGroup resolveChild(String name) throws InvalidObjectException;

    /**
     * Returns the id of the problem with the given name in this group. Root groups cannot hold problems; the groups
     * that can implement this through their public type.
     */
    default ProblemId problemId(String name) {
        throw new IllegalArgumentException("Problem group '" + getName() + "' is a root group and cannot hold problems, but problem '" + name + "' was requested");
    }
}
