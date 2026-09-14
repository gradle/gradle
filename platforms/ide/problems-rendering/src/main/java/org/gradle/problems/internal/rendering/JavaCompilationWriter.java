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

package org.gradle.problems.internal.rendering;

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.ProblemInternal;

import java.io.PrintWriter;

/**
 * Renders Java compilation problems as their details only: for javac diagnostics the details already carry the
 * formatted compiler output, including the location, so the usual header and body would duplicate it. A problem in the
 * group without details, such as a failure to start the compiler, gets the default rendering.
 */
class JavaCompilationWriter implements SelectiveProblemWriter {

    private final DefaultProblemWriter fallback = new DefaultProblemWriter();

    @Override
    public void write(ProblemInternal problem, RenderOptions options, PrintWriter output) {
        String details = problem.getDetails();
        if (details == null) {
            fallback.write(problem, options, output);
        } else {
            output.print(details);
        }
    }

    @Override
    public boolean accepts(ProblemId problemId) {
        return JavaCompilationProblems.isJavaCompilationGroup(problemId.getGroup());
    }
}
