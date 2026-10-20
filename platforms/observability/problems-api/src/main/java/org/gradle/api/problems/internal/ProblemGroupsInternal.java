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

import org.gradle.api.problems.ProblemGroups;
import org.gradle.api.problems.ProblemId;

import java.util.List;

/**
 * Internal view of the {@link ProblemGroups} of the {@code Problems} service, for Gradle code that reads back a problem
 * identity it wrote as plain names.
 */
public interface ProblemGroupsInternal extends ProblemGroups {

    /**
     * Returns the id of the problem with the given name in the group with the given path of names, root group first.
     * <p>
     * The path is resolved against the predefined hierarchy: predefined groups resolve to their canonical instances and
     * other names to the groups that {@code group(name)} would create at that place.
     *
     * @throws IllegalArgumentException if the path does not start with a predefined root group, names a group that
     * cannot exist at that place, or ends in a group that cannot hold problems
     */
    ProblemId problemId(List<String> groupPath, String name);
}
