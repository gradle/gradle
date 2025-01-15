/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.api.problems.internal.DefaultProblemId;

/**
 * Represents a unique identifier for a problem definition.
 * <p>
 * Problem IDs are defined with a name and with group hierarchy. For example, in the domain of Java compilation problems, the id object for unused variable warnings would be:
 * <pre>
 * ProblemId(name: unused-variable, displayName: Unused Variable, group:
 *     ProblemGroup(name: java, displayName: Java compilation, parent:
 *         ProblemGroup(name: compilation, displayName: Compilation, parent: null)))
 * </pre>
 * From the name fields a fully qualified name can be inferred: {@code compilation:java:unused-variable}.
 * Also, the display names are intended for display on some user interface. Consumers of problem reports can build a tree representation of problems:
 * <pre>
 * (Problem view)
 * Compilation
 *     Java compilation
 *         Foo.java#L10: unused variable a
 *         Foo.java#L20: unused variable b

 * </pre>
 *
 * @since 8.13
 * @see org.gradle.api.problems.internal.ProblemDefinition
 */
@Incubating
public abstract class ProblemId {

    /**
     * Constructor.
     *
     * @since 8.13
     */
    protected ProblemId() {
        // clients should use the static create() method
    }

    /**
     * The name of the problem.
     * <p>
     * Then name should be used categorize to create a fully qualified name and to categorize a set of problems. The name itself does not need to be unique, the uniqueness is determined the name and
     * the group hierarchy.
     *
     * @since 8.13
     */
    public abstract String getName();

    /**
     * A human-readable label describing the problem ID.
     * <p>
     * The display name should be used to present the problem to the user.
     *
     * @since 8.13
     */
    public abstract String getDisplayName();

    /**
     * The parent group.
     *
     * @since 8.13
     */
    public abstract ProblemGroup getGroup();

    /**
     * Creates a new problem id.
     *
     * @param name the name of the problem. The convention is to use kebab-case (ie lower case with hyphens).
     * @param displayName the user-friendly display name of the problem
     * @param group the group to which the problem belongs
     * @return the new problem id
     * @since 8.13
     */
    public static ProblemId create(String name, String displayName, ProblemGroup group) {
        return new DefaultProblemId(name, displayName, group);
    }
}
