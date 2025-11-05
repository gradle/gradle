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
import org.gradle.api.problems.internal.InternalProblem;

import java.io.PrintWriter;

class DefaultProblemWriter implements SelectiveProblemWriter {

    // The header and the body are rendered separately to simulate how to enforce unified headers for contributed renderers.
    private static final PartialProblemWriter HEADER_WRITER = new ProblemHeaderWriter();
    private static final PartialProblemWriter BODY_WRITER = new ProblemBodyWriter();

    @Override
    public void write(InternalProblem problem, RenderOptions options, PrintWriter output) {
        HEADER_WRITER.write(problem, options, output);
        BODY_WRITER.write(problem, options, output);
    }

    @Override
    public boolean accepts(ProblemId problemId) {
        return true;
    }
}
