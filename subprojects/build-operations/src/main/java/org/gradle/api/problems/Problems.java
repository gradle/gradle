/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.internal.operations.GradleExceptionWithContext;
import org.gradle.internal.problems.DefaultProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Prototype Problems API.
 *
 * @since 8.3
 */
@Incubating
public class Problems {

    private static final ThreadLocal<List<Problem>> PROBLEMS = new ThreadLocal<List<Problem>>();
    public static List<Problem> removeAllProblems() {
        List<Problem> objects = PROBLEMS.get();
        return objects == null ? Collections.<Problem>emptyList() : objects;

    }

    public static void reportWarning(String message) {
        addProblem(new DefaultProblem(message, "WARNING", null, null, null));
    }

    public static void reportFailure(String message, String file, Integer line, Integer column, Throwable cause) {
        addProblem(new DefaultProblem(message, "ERROR", file, line, column));
        throw new GradleExceptionWithContext(cause);
    }

    private static void addProblem(Problem problem) {
        List<Problem> problemList = PROBLEMS.get();
        if (problemList == null) {
            problemList = new ArrayList<Problem>();
        }
        problemList.add(problem);
        PROBLEMS.set(problemList);
    }
}
