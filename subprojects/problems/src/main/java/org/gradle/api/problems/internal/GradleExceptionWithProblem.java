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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.Problem;

import java.util.Collections;
import java.util.List;

/**
 * Represents some chunk of work.
 *
 * @since 8.3
 */
public class GradleExceptionWithProblem extends RuntimeException {
    private final List<Problem> problems;

    public GradleExceptionWithProblem(Problem problem) {
        super(problem.getCause());
        this.problems = Collections.singletonList(problem);
    }

    public GradleExceptionWithProblem(List<Problem> problems, Throwable cause) {
        super(cause);
        this.problems = Collections.unmodifiableList(problems);
    }

    public List<Problem> getProblems() {
        return problems;
    }
}
