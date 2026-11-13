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
import org.gradle.api.problems.internal.LegacyProblemIdentityUsage;

/**
 * Represents a unique identifier for a problem definition.
 * <p>
 * Problem IDs are defined with a name and with group hierarchy. For example, in the domain of Java compilation problems, the id object for unused variable warnings would be:
 * <pre>
 * ProblemId(name: Unused variable, group:
 *     ProblemGroup(name: Java, parent:
 *         ProblemGroup(name: Compilation, parent: null)))
 * </pre>
 * Such an id is created from the predefined groups, see {@link Problems#getGroups()}:
 * {@code getGroups().getCompilation().getJava().problemId("Unused variable")}.
 * Consumers of problem reports can build a tree representation of problems from the names:
 * <pre>
 * (Problem view)
 * Compilation
 *     Java
 *         Foo.java#L10: unused variable a
 *         Foo.java#L20: unused variable b
 * </pre>
 *
 * @since 8.13
 * @see org.gradle.api.problems.ProblemDefinition
 */
@Incubating
public abstract class ProblemId {

    /**
     * Constructor.
     *
     * @since 8.13
     * @deprecated Subclassing ProblemId outside of Gradle is deprecated and will not be supported in Gradle 10.
     * Use the groups from {@link Problems#getGroups()} instead.
     */
    @Deprecated
    protected ProblemId() {
        // clients should use the groups from Problems.getGroups()
        // Gradle 10: make this constructor package-private instead of removing it, since removing it would make javac
        // generate a public one. Subclasses in this package keep working; internal subclasses must move into this package.
        if (!(this instanceof DefaultProblemId)) {
            LegacyProblemIdentityUsage.used(LegacyProblemIdentityUsage.Usage.ID_SUBCLASS);
        }
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
     * @deprecated Use {@link #getName()} instead. The display name of an id created from a group of {@link Problems#getGroups()} is its name.
     * This method will be removed in Gradle 10.
     */
    @Deprecated
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
     * @param name the name of the problem. Cannot be blank (i.e., {@code null}, empty string, or only whitespaces).
     * @param displayName the user-friendly display name of the problem. Cannot be blank (i.e., {@code null}, empty string, or only whitespaces).
     * @param group the group to which the problem belongs. Cannot be null.
     * @return the new problem id
     * @since 8.13
     * @deprecated Create the id from a predefined group of {@link Problems#getGroups()} instead, with
     * {@link SecondLevelProblemGroup#problemId(String)}, {@link ThirdLevelProblemGroup#problemId(String)} or {@link UndefinedProblemGroup#problemId(String)}.
     * This method will be removed in Gradle 11.
     */
    @Deprecated
    public static ProblemId create(String name, String displayName, ProblemGroup group) {
        return new DefaultProblemId(name, displayName, group);
    }
}
