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

import java.util.Arrays;
import java.util.List;

class ProblemWriterRegistry {

    public static final ProblemWriterRegistry INSTANCE = new ProblemWriterRegistry();

    // ordered by priority
    private static final List<SelectiveProblemWriter> WRITERS = Arrays.asList(new JavaCompilationWriter(), new DefaultProblemWriter());

    private ProblemWriterRegistry() {
    }

    public SelectiveProblemWriter problemWriterFor(ProblemId problemId) {
        for (SelectiveProblemWriter writer : WRITERS) {
            if (writer.accepts(problemId)) {
                return writer;
            }
        }
        throw new IllegalStateException("No writer found for problem Id: " + problemId);
    }
}
