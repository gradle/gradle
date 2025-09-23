/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.problems;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProblemUtils {

    public static List<ProblemGroup> groups(InternalProblem problem) {
        List<ProblemGroup> groups = new ArrayList<>(4);
        ProblemGroup group = problem.getDefinition().getId().getGroup();
        while (group != null) {
            groups.add(group);
            group = group.getParent();
        }
        Collections.reverse(groups);
        return groups;
    }
}
