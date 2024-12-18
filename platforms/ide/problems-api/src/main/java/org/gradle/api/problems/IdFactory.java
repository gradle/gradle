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
import org.gradle.api.problems.internal.DefaultIdFactory;

/**
 * A factory for creating problem ids and problem groups.
 * <p>
 * Clients should use the {@link #instance()} method to obtain an instance of this factory.
 * <p>
 * Custom implementation for this class is not supported.
 *
 * @since 8.13
 */
@Incubating
public abstract class IdFactory {

    /**
     * Constructor.
     *
     * @since 8.13
     */
    protected IdFactory() {
    }

    /**
     * Returns the default instance of the factory.
     *
     * @return the instance
     * @since 8.13
     */
    public static IdFactory instance() {
        return DefaultIdFactory.INSTANCE;
    }

    /**
     * Creates a new root problem i.e. a group with no parent.
     *
     * @param name the name of the group
     * @param displayName the user-friendly display name of the group
     * @return the new group
     * @since 8.13
     */
    public abstract ProblemGroup createRootProblemGroup(String name, String displayName);

    /**
     * Creates a new problem group.
     *
     * @param name the name of the group
     * @param displayName the user-friendly display name of the group
     * @param parent the parent group
     * @return the new group
     * @since 8.13
     */
    public abstract ProblemGroup createProblemGroup(String name, String displayName, ProblemGroup parent);

    /**
     * Creates a new problem id.
     *
     * @param name the name of the problem
     * @param displayName the user-friendly display name of the problem
     * @param group the group to which the problem belongs
     * @return the new problem id
     * @since 8.13
     */
    public abstract ProblemId createProblemId(String name, String displayName, ProblemGroup group);
}
