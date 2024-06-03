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

package org.gradle.plugin.devel.tasks.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.gradle.api.Incubating;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemTransformer;
import org.gradle.internal.operations.OperationIdentifier;

import java.util.Collection;

/**
 * Tracks validation issues reported via the Problems API.
 *
 * @since 8.9
 */
@Incubating
public class ValidationProblemTracker implements ProblemTransformer {

    private static Multimap<Long, Problem> problems = ArrayListMultimap.create();

    /**
     * Creates a new instance.
     *
     * @since 8.9
     */
    public ValidationProblemTracker() {
    }

    @Override
    public Problem transform(Problem problem, OperationIdentifier id) {
        if (problemInGroup(problem.getDefinition().getId(), GradleCoreProblemGroup.validation())) {
            problems.put(id.getId(), problem);
        }
        return problem;
    }

    private static boolean problemInGroup(ProblemId id, ProblemGroup group) {
        ProblemGroup candidateGroup = id.getGroup();
        while (candidateGroup != null) {
            if (candidateGroup.equals(group)) {
                return true;
            }
            candidateGroup = candidateGroup.getParent();
        }
        return false;
    }

    /**
     * Returns all validation problems that were reported for the given operation.
     *
     * @param operationId The operation identifier
     * @since 8.9
     */
    @Incubating
    public static Collection<Problem> problemsReportedInOperation(long operationId) {
        return ImmutableList.copyOf(problems.get(operationId));
    }
}
